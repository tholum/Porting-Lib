package io.github.fabricators_of_create.porting_lib.models;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.mojang.math.Transformation;

import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class CompositeModel implements UnbakedModel {
	private static final Logger LOGGER = LogManager.getLogger();

	private final BlockModel owner;
	private final ImmutableMap<String, BlockModel> children;
	private final ImmutableList<String> itemPasses;

	public CompositeModel(BlockModel owner, ImmutableMap<String, BlockModel> children, ImmutableList<String> itemPasses) {
		this.owner = owner;
		this.children = children;
		this.itemPasses = itemPasses;
	}

	@Override
	public Collection<ResourceLocation> getDependencies() {
		return Collections.emptyList();
	}

	@Override
	public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter) {
		children.values().forEach(child -> child.resolveParents(modelGetter));
	}

	@Nullable
	@Override
	public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ResourceLocation modelLocation) {
		Material particleLocation = owner.getMaterial("particle");
		TextureAtlasSprite particle = spriteGetter.apply(particleLocation);

		var bakedPartsBuilder = ImmutableMap.<String, BakedModel>builder();
		for (var entry : children.entrySet()) {
			var name = entry.getKey();
			var model = entry.getValue();
			bakedPartsBuilder.put(name, model.bake(baker, model, spriteGetter, modelState, modelLocation, true));
		}
		var bakedParts = bakedPartsBuilder.build();

		var itemPassesBuilder = ImmutableList.<BakedModel>builder();
		for (String name : this.itemPasses) {
			var model = bakedParts.get(name);
			if (model == null)
				throw new IllegalStateException("Specified \"" + name + "\" in \"item_render_order\", but that is not a child of this model.");
			itemPassesBuilder.add(model);
		}

		return new Baked(true, owner.getGuiLight().lightLikeBlock(), owner.hasAmbientOcclusion(), particle, owner.getTransforms(), owner.getItemOverrides(baker, owner), bakedParts, itemPassesBuilder.build());
	}

	public static class Baked implements BakedModel, FabricBakedModel {
		private final boolean isAmbientOcclusion;
		private final boolean isGui3d;
		private final boolean isSideLit;
		private final TextureAtlasSprite particle;
		private final ItemOverrides overrides;
		private final ItemTransforms transforms;
		private final ImmutableMap<String, BakedModel> children;
		private final ImmutableList<BakedModel> itemPasses;

		public Baked(boolean isGui3d, boolean isSideLit, boolean isAmbientOcclusion, TextureAtlasSprite particle, ItemTransforms transforms, ItemOverrides overrides, ImmutableMap<String, BakedModel> children, ImmutableList<BakedModel> itemPasses) {
			this.children = children;
			this.isAmbientOcclusion = isAmbientOcclusion;
			this.isGui3d = isGui3d;
			this.isSideLit = isSideLit;
			this.particle = particle;
			this.overrides = overrides;
			this.transforms = transforms;
			this.itemPasses = itemPasses;
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
			List<List<BakedQuad>> quadLists = new ArrayList<>();
			for (Map.Entry<String, BakedModel> entry : children.entrySet()) {
				quadLists.add(entry.getValue().getQuads(state, side, rand));
			}
			return ConcatenatedListView.of(quadLists);
		}

		@Override
		public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
			for (Map.Entry<String, BakedModel> entry : children.entrySet()) {
				((FabricBakedModel) entry.getValue()).emitBlockQuads(blockView, state, pos, randomSupplier, context);
			}
		}

		@Override
		public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
			for (Map.Entry<String, BakedModel> entry : children.entrySet()) {
				((FabricBakedModel) entry.getValue()).emitItemQuads(stack, randomSupplier, context);
			}
		}

		@Override
		public boolean useAmbientOcclusion() {
			return isAmbientOcclusion;
		}

		@Override
		public boolean isGui3d() {
			return isGui3d;
		}

		@Override
		public boolean usesBlockLight() {
			return isSideLit;
		}

		@Override
		public boolean isCustomRenderer() {
			return false;
		}

		@Override
		public TextureAtlasSprite getParticleIcon() {
			return particle;
		}

		@Override
		public ItemOverrides getOverrides() {
			return overrides;
		}

		@Override
		public ItemTransforms getTransforms() {
			return transforms;
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		@Nullable
		public BakedModel getPart(String name) {
			return children.get(name);
		}
	}
}
