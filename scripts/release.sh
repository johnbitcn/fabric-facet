#!/usr/bin/env bash
#
# Facet 本地发布脚本
#
# 职责：发布候选的准备与校验（守卫 → 干净构建 → 机械枚举 → 产物校验 →
# 候选哈希），以及验收后创建 annotated tag 并推送，触发 CI 权威构建。
#
# 正式发布以 tag 触发的 GitHub Actions 构建为准，本脚本不重复最终构建；
# release/SHA256SUMS.txt 仅供诊断，最终哈希以 CI 校验结果为准。
#
# 用法:
#   scripts/release.sh              # 候选模式：构建+枚举+校验+暂存，不打 tag
#   scripts/release.sh --publish    # 发布模式：候选校验后创建并推送 tag
#
set -euo pipefail

cd "$(dirname "$0")/.."

MODE=candidate
if [[ "${1:-}" == "--publish" ]]; then
	MODE=publish
fi

echo "== 1/6 环境守卫 =="

branch="$(git branch --show-current)"
if [[ "$branch" != "main" ]]; then
	echo "必须在 main 分支发布（当前: ${branch}）" >&2
	exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
	echo "工作树有未提交改动，拒绝发布" >&2
	git status --short >&2
	exit 1
fi

git fetch origin --quiet
local_head="$(git rev-parse HEAD)"
remote_main="$(git rev-parse origin/main)"
if [[ "$local_head" != "$remote_main" ]]; then
	echo "本地 main（$local_head）与 origin/main（$remote_main）不一致，先对齐再发布" >&2
	exit 1
fi

git diff --check

echo "== 2/6 版本与发布目标 =="

mod_version="$(awk -F= '/^mod_version=/{print $2; exit}' gradle.properties)"
if [[ ! "$mod_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
	echo "mod_version 格式非法: $mod_version" >&2
	exit 1
fi
echo "mod_version=$mod_version"

# 发布目标必须与 .github/workflows/publish.yml 的 staging 列表保持一致。
release_targets=(
	fabric-26.1
	fabric-26.2
	fabric-26.3-snapshot-10
	neoforge-26.1
	neoforge-26.1.2
	neoforge-26.2
)

build_targets=()
while IFS= read -r line; do
	if [[ "$line" =~ ^include\ \'versions:(.+)\'$ ]]; then
		build_targets+=("${BASH_REMATCH[1]}")
	fi
done < settings.gradle
if [[ ${#build_targets[@]} -eq 0 ]]; then
	echo "无法从 settings.gradle 解析模块列表" >&2
	exit 1
fi

for target in "${release_targets[@]}"; do
	found=0
	for candidate in "${build_targets[@]}"; do
		if [[ "$candidate" == "$target" ]]; then
			found=1
		fi
	done
	if [[ $found -ne 1 ]]; then
		echo "发布目标 $target 不在 settings.gradle 中" >&2
		exit 1
	fi
done

if git rev-parse --verify "refs/tags/v$mod_version" >/dev/null 2>&1; then
	echo "tag v$mod_version 已存在，拒绝重复发布" >&2
	exit 1
fi

echo "== 3/6 干净构建全部模块 =="

./gradlew --no-daemon clean build --console=plain

echo "== 4/6 机械枚举并校验产物 =="

release_jars=()
for target in "${release_targets[@]}"; do
	libdir="versions/$target/build/libs"
	if [[ ! -d "$libdir" ]]; then
		echo "缺少构建输出目录 $libdir" >&2
		exit 1
	fi

	main_jars=()
	sources_jars=()
	for jar in "$libdir"/*.jar; do
		[[ -f "$jar" ]] || continue
		if [[ "$jar" == *-sources.jar ]]; then
			sources_jars+=("$jar")
		else
			main_jars+=("$jar")
		fi
	done

	if [[ ${#main_jars[@]} -ne 1 ]]; then
		echo "$target 主 JAR 数量异常: ${#main_jars[@]}" >&2
		exit 1
	fi
	if [[ ${#sources_jars[@]} -ne 1 ]]; then
		echo "$target sources JAR 数量异常: ${#sources_jars[@]}" >&2
		exit 1
	fi

	main_jar="${main_jars[0]}"
	base="$(basename "$main_jar")"
	if [[ ! "$base" =~ ^Facet-(Fabric|NeoForge)-$mod_version- ]]; then
		echo "$target 主 JAR 命名或版本不符: $base" >&2
		exit 1
	fi

	unzip -tq "$main_jar" >/dev/null || {
		echo "$target 主 JAR ZIP 完整性检查失败" >&2
		exit 1
	}

	if [[ "$target" == fabric-* ]]; then
		unzip -p "$main_jar" fabric.mod.json | grep -q '"version": "'"$mod_version"'"' || {
			echo "$target fabric.mod.json 版本不符（期望 $mod_version）" >&2
			exit 1
		}
	else
		unzip -p "$main_jar" META-INF/neoforge.mods.toml | grep -q '^version="'"$mod_version"'"$' || {
			echo "$target neoforge.mods.toml 版本不符（期望 $mod_version）" >&2
			exit 1
		}
	fi

	release_jars+=("$main_jar")
	echo "  ok $base"
done

echo "== 5/6 候选哈希（诊断用，非最终）=="

rm -rf release
mkdir -p release
cp "${release_jars[@]}" release/
(
	cd release
	sha256sum *.jar | sort > SHA256SUMS.txt
	sha256sum --check SHA256SUMS.txt
)
cat release/SHA256SUMS.txt

if [[ "$MODE" == "candidate" ]]; then
	echo
	echo "候选就绪：${#release_jars[@]} 个主 JAR 已暂存到 release/（SHA256SUMS.txt 仅供诊断）"
	echo "游戏内验收通过后，运行 scripts/release.sh --publish 创建 tag 并推送。"
	exit 0
fi

echo "== 6/6 创建 annotated tag 并推送 =="

git tag -a "v$mod_version" -m "Release $mod_version"
git push origin "v$mod_version"

echo
echo "发布已触发：tag v$mod_version @ $local_head"
echo "预期 GitHub Release 标题: Facet v$mod_version"
echo "资产: ${#release_jars[@]} 个主 JAR + SHA256SUMS.txt"
