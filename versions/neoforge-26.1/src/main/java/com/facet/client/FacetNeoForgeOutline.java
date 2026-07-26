package com.facet.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = FacetNeoForgeOutline.MOD_ID, dist = Dist.CLIENT)
public final class FacetNeoForgeOutline {
	static final String MOD_ID = "facet";
	static final Logger LOGGER = LoggerFactory.getLogger("Facet NeoForge Outline");
	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(MOD_ID, "keybinds"));
	private static final KeyMapping TOGGLE_OUTLINE_KEY = new KeyMapping(
			"key.facet.toggle_outline",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			KEY_CATEGORY);
	private static final KeyMapping GRAFFITI_KEY = new KeyMapping(
			"key.facet.graffiti",
			InputConstants.Type.KEYSYM,
			InputConstants.KEY_G,
			KEY_CATEGORY);
	private static final KeyMapping TOGGLE_HOVER_OUTLINE_KEY = new KeyMapping(
			"key.facet.toggle_hover_outline",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			KEY_CATEGORY);
	private static final KeyMapping TOGGLE_DISTANCE_HUD_KEY = new KeyMapping(
			"key.facet.toggle_distance_hud",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			KEY_CATEGORY);
	private static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
			"key.facet.open_settings",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			KEY_CATEGORY);
	private static final GraffitiClientAccess GRAFFITI_CLIENT_ACCESS = new GraffitiClientAccess() {
		@Override
		public void showScreen(Minecraft minecraft, Screen screen) {
			FacetNeoForgePlatform.showScreen(minecraft, screen);
		}

		@Override
		public void rebuildBlockSection(Minecraft minecraft, BlockPos pos) {
			if (minecraft.level == null) {
				return;
			}
			int sectionX = SectionPos.blockToSectionCoord(pos.getX());
			int sectionY = SectionPos.blockToSectionCoord(pos.getY());
			int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
			minecraft.level.setSectionRangeDirty(sectionX, sectionY, sectionZ, sectionX, sectionY, sectionZ);
		}

		@Override
		public String worldScope(Minecraft minecraft, ClientLevel level) {
			if (minecraft.getCurrentServer() != null) {
				return "server:" + minecraft.getCurrentServer().ip;
			}
			if (minecraft.getSingleplayerServer() != null) {
				return "singleplayer:" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
			}
			return "unknown";
		}
	};

	public FacetNeoForgeOutline(IEventBus modBus, ModContainer modContainer) {
		modContainer.registerConfig(ModConfig.Type.CLIENT, FacetNeoForgeOutlineConfig.SPEC, "facet-neoforge-client.toml");
		modContainer.registerExtensionPoint(IConfigScreenFactory.class,
				(IConfigScreenFactory) (container, modListScreen) -> new FacetNeoForgeConfigScreen(modListScreen));
		GraffitiStore.initialize(FMLPaths.CONFIGDIR.get());
		GraffitiStore.load();
		modBus.addListener(FacetNeoForgeOutlineRenderer::modifyBakingResult);
		modBus.addListener(FacetNeoForgeOutline::registerKeyMappings);
		modBus.addListener(FacetNeoForgeOutline::registerGuiLayers);
		modBus.addListener(FacetNeoForgeOutline::handleConfigReload);
		NeoForge.EVENT_BUS.addListener(FacetNeoForgeOutline::handleClientTick);
		NeoForge.EVENT_BUS.addListener(FacetNeoForgeHoverOutline::extract);
		NeoForge.EVENT_BUS.addListener(FacetNeoForgeHoverOutline::renderDistant);
		NeoForge.EVENT_BUS.addListener(FacetNeoForgeDistanceHud::renderPath);
		NeoForge.EVENT_BUS.addListener(FacetNeoForgePlacementPreview::render);
		NeoForge.EVENT_BUS.addListener(FacetNeoForgeOutline::handleChunkLoad);
		NeoForge.EVENT_BUS.addListener(FacetNeoForgeOutline::handleClientLogout);
		LOGGER.info("Registering Minecraft 26.1 NeoForge block-outline renderer");
	}

	private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.registerCategory(KEY_CATEGORY);
		event.register(TOGGLE_OUTLINE_KEY);
		event.register(GRAFFITI_KEY);
		event.register(TOGGLE_HOVER_OUTLINE_KEY);
		event.register(TOGGLE_DISTANCE_HUD_KEY);
		event.register(OPEN_SETTINGS_KEY);
	}

	private static void registerGuiLayers(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.CROSSHAIR, Identifier.fromNamespaceAndPath(MOD_ID, "distance_hud"),
				(graphics, partialTick) -> FacetNeoForgeDistanceHud.renderHud(graphics));
	}

	private static void handleClientTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			GraffitiStore.setContext(GRAFFITI_CLIENT_ACCESS.worldScope(minecraft, minecraft.level), minecraft.level.dimension().identifier());
		}

		while (TOGGLE_OUTLINE_KEY.consumeClick()) {
			boolean enabled = FacetNeoForgeOutlineConfig.toggleEnabled();
			queueOutlineChunkRebuild();
			LOGGER.info("Facet NeoForge block outlines {}", enabled ? "enabled" : "disabled");
		}

		while (GRAFFITI_KEY.consumeClick()) {
			openGraffitiWheel(minecraft);
		}

		while (TOGGLE_HOVER_OUTLINE_KEY.consumeClick()) {
			boolean enabled = FacetNeoForgeOutlineConfig.toggleHoverEnabled();
			LOGGER.info("Facet NeoForge hover outline {}", enabled ? "enabled" : "disabled");
		}

		while (TOGGLE_DISTANCE_HUD_KEY.consumeClick()) {
			FacetNeoForgeDistanceHud.toggle();
		}

		while (OPEN_SETTINGS_KEY.consumeClick()) {
			FacetNeoForgePlatform.showScreen(minecraft, new FacetNeoForgeConfigScreen(null));
		}

		GraffitiStore.flush();
	}

	private static void handleChunkLoad(ChunkEvent.Load event) {
		if (!(event.getLevel() instanceof ClientLevel level)) {
			return;
		}

		Minecraft.getInstance().execute(() -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level != level) {
				return;
			}
			GraffitiStore.setContext(GRAFFITI_CLIENT_ACCESS.worldScope(minecraft, level), level.dimension().identifier());
			GraffitiStore.reconcileChunk(level, event.getChunk().getPos());
		});
	}

	private static void handleClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		GraffitiStore.flush();
	}

	public static void reconcileGraffitiBlock(ClientLevel level, BlockPos pos, BlockState state) {
		if (!GraffitiStore.reconcileConfirmedBlock(level, pos, state)) {
			return;
		}

		int sectionX = SectionPos.blockToSectionCoord(pos.getX());
		int sectionY = SectionPos.blockToSectionCoord(pos.getY());
		int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
		level.setSectionRangeDirty(sectionX, sectionY, sectionZ, sectionX, sectionY, sectionZ);
	}

	private static void openGraffitiWheel(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null
				|| !(minecraft.hitResult instanceof BlockHitResult hitResult)
				|| hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos pos = hitResult.getBlockPos();
		BlockState state = minecraft.level.getBlockState(pos);
		Direction direction = hitResult.getDirection();
		GraffitiType currentType = GraffitiStore.getType(pos, direction);
		GraffitiEligibility.Result result = GraffitiEligibility.evaluate(minecraft.level, pos, state, direction);
		if (currentType == null && result != GraffitiEligibility.Result.ALLOWED) {
			minecraft.player.sendOverlayMessage(Component.translatable(switch (result) {
				case NON_SOLID -> "message.facet.graffiti.non_solid";
				case FUNCTIONAL -> "message.facet.graffiti.functional";
				case INCOMPLETE_FACE -> "message.facet.graffiti.incomplete_face";
				case ALLOWED -> throw new IllegalStateException("Allowed graffiti result was rejected");
			}));
			return;
		}

		GRAFFITI_CLIENT_ACCESS.showScreen(minecraft, new GraffitiWheelScreen(
				GRAFFITI_CLIENT_ACCESS.worldScope(minecraft, minecraft.level), minecraft.level.dimension().identifier(),
				pos.immutable(), direction, BuiltInRegistries.BLOCK.getKey(state.getBlock()), GRAFFITI_CLIENT_ACCESS));
	}

	private static void handleConfigReload(ModConfigEvent.Reloading event) {
		if (event.getConfig().getSpec() == FacetNeoForgeOutlineConfig.SPEC) {
			queueOutlineChunkRebuild();
		}
	}

	static void queueOutlineChunkRebuild() {
		Minecraft.getInstance().execute(FacetNeoForgeOutline::rebuildOutlineChunks);
	}

	private static void rebuildOutlineChunks() {
		FacetNeoForgePlatform.rebuildChunks(Minecraft.getInstance());
	}
}
