#!/usr/bin/env python3
import importlib.util
import io
import json
import os
from pathlib import Path
import unittest
from unittest.mock import MagicMock, patch
from urllib.error import HTTPError

spec = importlib.util.spec_from_file_location(
    "description_sync", Path(__file__).with_name("update-modrinth-description.py"))
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


class ModrinthDescriptionTest(unittest.TestCase):
    def test_only_exact_project_pages_line_is_removed(self):
        readme = '# Facet\n\n' + sync.PROJECT_PAGES_LINE + '\n\nUnicode: 中文 "quoted"\n'
        self.assertEqual('# Facet\n\n\nUnicode: 中文 "quoted"\n', sync.build_description(readme))

    def test_crlf_and_remaining_markdown_are_preserved(self):
        self.assertEqual('# Facet\r\n[License](./LICENSE)\r\n', sync.build_description(
            '# Facet\r\n' + sync.PROJECT_PAGES_LINE + '\r\n[License](./LICENSE)\r\n'))

    def test_invalid_readme_fails(self):
        for readme in ['', '# Facet\n', sync.PROJECT_PAGES_LINE,
                       sync.PROJECT_PAGES_LINE + '\n' + sync.PROJECT_PAGES_LINE]:
            with self.subTest(readme=readme), self.assertRaises(ValueError):
                sync.build_description(readme)

    def test_current_readme_is_not_modified(self):
        path = Path(__file__).resolve().parent.parent / 'README.md'
        original = path.read_bytes()
        readme = original.decode('utf-8')
        self.assertEqual(readme.replace(sync.PROJECT_PAGES_LINE + '\n', '', 1),
                         sync.build_description(readme))
        self.assertEqual(original, path.read_bytes())

    def responses(self, body):
        write = MagicMock()
        write.__enter__.return_value.status = 204
        read = MagicMock()
        read.__enter__.return_value.read.return_value = json.dumps({'body': body}).encode('utf-8')
        return [write, read]

    @patch.object(sync, 'build_opener')
    def test_patch_changes_only_body_and_get_verifies_it(self, build_opener):
        opener = build_opener.return_value
        opener.open.side_effect = self.responses('中文 "quoted"\n')
        sync.sync_description('lW1ulmxe', 'test-token', '中文 "quoted"\n')
        self.assertEqual(2, opener.open.call_count)
        write, read = [call.args[0] for call in opener.open.call_args_list]
        self.assertEqual('PATCH', write.get_method())
        self.assertEqual({'body': '中文 "quoted"\n'}, json.loads(write.data))
        self.assertEqual('test-token', write.get_header('Authorization'))
        self.assertEqual('application/json', write.get_header('Content-type'))
        self.assertEqual('https://api.modrinth.com/v2/project/lW1ulmxe', write.full_url)
        self.assertEqual(write.full_url, read.full_url)
        self.assertEqual('GET', read.get_method())
        self.assertTrue(all(call.kwargs == {'timeout': 30} for call in opener.open.call_args_list))
        self.assertIsInstance(build_opener.call_args.args[0], sync.NoRedirect)

    @patch.object(sync, 'build_opener')
    def test_read_back_mismatch_fails(self, build_opener):
        build_opener.return_value.open.side_effect = self.responses('stale body')
        with self.assertRaisesRegex(RuntimeError, 'read-back'):
            sync.sync_description('lW1ulmxe', 'test-token', 'new body')

    @patch.object(sync, 'build_opener')
    def test_unexpected_patch_response_fails_without_read_back(self, build_opener):
        response = MagicMock()
        response.__enter__.return_value.status = 200
        build_opener.return_value.open.return_value = response
        with self.assertRaisesRegex(RuntimeError, '204'):
            sync.sync_description('lW1ulmxe', 'test-token', 'body')
        self.assertEqual(1, build_opener.return_value.open.call_count)

    @patch.object(sync, 'build_opener')
    def test_missing_secrets_do_not_send_requests(self, build_opener):
        for project, token in [('', 'token'), ('project', '')]:
            with self.subTest(project=project), self.assertRaises(ValueError):
                sync.sync_description(project, token, 'body')
        build_opener.assert_not_called()

    def test_redirect_is_not_followed(self):
        self.assertIsNone(sync.NoRedirect().redirect_request(
            None, None, 302, 'Found', {}, 'https://example.com'))

    @patch.object(sync, 'sync_description')
    def test_errors_fail_without_logging_secrets(self, update):
        for error in [HTTPError('https://api.modrinth.com', 403, 'test-token', {}, None),
                      ValueError('test-token')]:
            with self.subTest(error=type(error).__name__), patch.dict(os.environ, {
                    'MODRINTH_PROJECT_ID': 'lW1ulmxe', 'MODRINTH_TOKEN': 'test-token'}):
                update.side_effect = error
                with patch('sys.stderr', new_callable=io.StringIO) as stderr:
                    self.assertEqual(1, sync.main())
                    self.assertNotIn('test-token', stderr.getvalue())

    def test_standalone_workflow_only_syncs_description(self):
        root = Path(__file__).resolve().parent.parent
        workflow = (root / '.github/workflows/sync-modrinth-description.yml').read_text()
        self.assertIn('on: workflow_dispatch\n', workflow)
        self.assertIn('  contents: read\n', workflow)
        self.assertIn('run: python3 scripts/test-modrinth-description.py', workflow)
        self.assertIn('run: python3 scripts/update-modrinth-description.py', workflow)
        self.assertIn('MODRINTH_PROJECT_ID: ${{ secrets.MODRINTH_PROJECT_ID }}', workflow)
        self.assertIn('MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}', workflow)
        for forbidden in ['gradlew', 'mc-publish', 'release.sh', 'upload-artifact',
                          'publish_modrinth', 'push:', 'tags:']:
            self.assertNotIn(forbidden, workflow)

    def test_both_workflows_gate_sync_on_modrinth_publishing(self):
        root = Path(__file__).resolve().parent.parent
        for name in ['publish.yml', 'publish-preview-26.4-snapshot-5.yml']:
            workflow = (root / '.github/workflows' / name).read_text()
            step = workflow.split('      - name: Update Modrinth project description\n', 1)[1].split('\n      - name:', 1)[0]
            self.assertIn("if: (github.event_name == 'push' || inputs.publish_modrinth) && "
                          "hashFiles('scripts/update-modrinth-description.py') != ''", step)
            self.assertIn('MODRINTH_PROJECT_ID: ${{ secrets.MODRINTH_PROJECT_ID }}', step)
            self.assertIn('MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}', step)
            self.assertIn('run: python3 scripts/update-modrinth-description.py', step)
            self.assertLess(workflow.index('name: Update Modrinth project description'),
                            workflow.index('name: Publish GitHub'))
            self.assertIn("if: hashFiles('scripts/test-modrinth-description.py') != ''", workflow)
            self.assertIn('run: python3 scripts/test-modrinth-description.py', workflow)


if __name__ == '__main__':
    unittest.main()
