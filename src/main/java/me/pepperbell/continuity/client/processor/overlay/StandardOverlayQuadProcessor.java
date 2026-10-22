package me.pepperbell.continuity.client.processor.overlay;

import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import me.pepperbell.continuity.api.client.ProcessingDataProvider;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.processor.AbstractQuadProcessor;
import me.pepperbell.continuity.client.processor.AbstractQuadProcessorFactory;
import me.pepperbell.continuity.client.processor.ConnectionPredicate;
import me.pepperbell.continuity.client.processor.DirectionMaps;
import me.pepperbell.continuity.client.processor.ProcessingDataKeys;
import me.pepperbell.continuity.client.processor.ProcessingPredicate;
import me.pepperbell.continuity.client.properties.overlay.OverlayPropertiesSection;
import me.pepperbell.continuity.client.properties.overlay.StandardOverlayCtmProperties;
import me.pepperbell.continuity.client.util.QuadUtil;
import me.pepperbell.continuity.client.util.RenderUtil;
import me.pepperbell.continuity.client.util.SpriteCalculator;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class StandardOverlayQuadProcessor extends AbstractQuadProcessor {
	@Nullable
	protected Set<Identifier> matchTilesSet;
	@Nullable
	protected Predicate<BlockState> matchBlocksPredicate;
	@Nullable
	protected Set<Identifier> connectTilesSet;
	@Nullable
	protected Predicate<BlockState> connectBlocksPredicate;
	protected ConnectionPredicate connectionPredicate;

	protected int tintIndex;
	@Nullable
	protected BlockState tintBlock;
	protected ChunkSectionLayer layer;
	protected TriState ao;

	public StandardOverlayQuadProcessor(TextureAtlasSprite[] sprites, ProcessingPredicate processingPredicate, @Nullable Set<Identifier> matchTilesSet, @Nullable Predicate<BlockState> matchBlocksPredicate, @Nullable Set<Identifier> connectTilesSet, @Nullable Predicate<BlockState> connectBlocksPredicate, ConnectionPredicate connectionPredicate, int tintIndex, @Nullable BlockState tintBlock, ChunkSectionLayer layer) {
		super(sprites, processingPredicate);
		this.matchTilesSet = matchTilesSet;
		this.matchBlocksPredicate = matchBlocksPredicate;
		this.connectTilesSet = connectTilesSet;
		this.connectBlocksPredicate = connectBlocksPredicate;
		this.connectionPredicate = connectionPredicate;

		this.tintIndex = tintIndex;
		this.tintBlock = tintBlock;
		this.layer = layer;
		this.ao = RenderUtil.aoFromTintBlock(tintBlock);

		// Turn all missing sprites into null, since it is more efficient to check for a null sprite than a missing
		// sprite. There is no functional difference between missing and null sprites for this processor.
		for (int i = 0; i < sprites.length; i++) {
			TextureAtlasSprite sprite = sprites[i];
			if (RenderUtil.isMissingSprite(sprite)) {
				sprites[i] = null;
			}
		}
	}

	@Override
	public ProcessingResult processQuadInner(MutableQuadView quad, TextureAtlasSprite sprite, BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, RandomSource random, int pass, ProcessingContext context) {
		Direction lightFace = quad.lightFace();
		SpriteCollector collector = getSprites(level, pos, appearanceState, state, lightFace, sprite, quad, DirectionMaps.getMap(lightFace)[0], context);
		if (collector != null) {
			QuadEmitter emitter = context.getExtraQuadEmitter();
			int tintColor = RenderUtil.getTintColor(tintBlock, level, pos, tintIndex);
			for (int i = 0; i < collector.spriteAmount; i++) {
				QuadUtil.emitOverlayQuad(emitter, quad, collector.sprites[i], tintColor, layer, ao);
			}
			collector.clear();
		}
		return ProcessingResult.NEXT_PROCESSOR;
	}

	protected static boolean matchesAny(Set<Identifier> tiles, Set<TextureAtlasSprite> sprites) {
		for (TextureAtlasSprite sprite : sprites) {
			if (tiles.contains(sprite.contents().name())) {
				return true;
			}
		}
		return false;
	}

	protected boolean appliesOverlay(BlockPos otherPos, BlockState otherAppearanceState, BlockState otherState, BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, Direction face, TextureAtlasSprite quadSprite) {
		if (!isFullBlock(otherState, level, otherPos)) {
			return false;
		}
		return isOverlaySource(otherPos, otherAppearanceState, otherState, level, pos, appearanceState, state, face, quadSprite);
	}

	// Similar to appliesOverlay for the in-plane neighbor in the given direction, but non-full-block neighbors, such
	// as a layers/slabs, also apply the overlay if its shape borders the quad's entire edge on that side.
	protected boolean appliesOverlayAlongEdge(QuadView quad, Direction dir, BlockPos otherPos, BlockState otherAppearanceState, BlockState otherState, BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, Direction face, TextureAtlasSprite quadSprite) {
		if (isFullBlock(otherState, level, otherPos)) {
			return isOverlaySource(otherPos, otherAppearanceState, otherState, level, pos, appearanceState, state, face, quadSprite);
		}
		return !otherState.isAir()
				&& isOverlaySource(otherPos, otherAppearanceState, otherState, level, pos, appearanceState, state, face, quadSprite)
				&& QuadUtil.isQuadEdgeBorderedBy(quad, dir, otherState.getShape(level, otherPos));
	}

	// OptiFine and Continuity never apply overlays from blocks with dynamic bounds. For mod compatibility, call isFullBlock
	// with the correct values and do not explictly check for dynamic bounds. For vanilla blocks, this change only makes
	// it so inactive pistons and shulkerboxes can apply overlays. Blocks that look like full blocks but have a smaller
	// collision shape, such as full-height layers (ie: property layers=8), count as "full blocks" too.
	protected static boolean isFullBlock(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		return state.isCollisionShapeFullBlock(level, pos) || (!state.isAir() && Block.isShapeFullBlock(state.getShape(level, pos)));
	}

	protected boolean isOverlaySource(BlockPos otherPos, BlockState otherAppearanceState, BlockState otherState, BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, Direction face, TextureAtlasSprite quadSprite) {
		if (connectBlocksPredicate != null) {
			if (!connectBlocksPredicate.test(otherAppearanceState)) {
				return false;
			}
		}
		if (connectTilesSet != null) {
			if (!matchesAny(connectTilesSet, SpriteCalculator.getSprites(otherAppearanceState, face))) {
				return false;
			}
		}
		return !connectionPredicate.shouldConnect(level, pos, appearanceState, state, otherPos, otherAppearanceState, otherState, face, quadSprite);
	}

	protected boolean hasSameOverlay(@Nullable BlockState otherAppearanceState, Direction face) {
		if (otherAppearanceState == null) {
			return false;
		}
		if (matchBlocksPredicate != null) {
			if (!matchBlocksPredicate.test(otherAppearanceState)) {
				return false;
			}
		}
		if (matchTilesSet != null) {
			if (!matchesAny(matchTilesSet, SpriteCalculator.getSprites(otherAppearanceState, face))) {
				return false;
			}
		}
		return true;
	}

	protected boolean appliesOverlayCorner(Direction dir0, Direction dir1, BlockPos.MutableBlockPos mutablePos, BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, Direction lightFace, TextureAtlasSprite quadSprite, boolean onBoundary) {
		mutablePos.setWithOffset(pos, dir0).move(dir1);
		BlockState otherState = level.getBlockState(mutablePos);
		BlockState otherAppearanceState = otherState.getAppearance(level, mutablePos, lightFace, state, pos);
		if (appliesOverlay(mutablePos, otherAppearanceState, otherState, level, pos, appearanceState, state, lightFace, quadSprite)) {
			if (!onBoundary) {
				return true;
			}
			mutablePos.move(lightFace);
			return !level.getBlockState(mutablePos).isSolidRender();
		}
		return false;
	}

	// Handles an inside corner where a partial block, such as a slab, sits in front of this face. The block beyond it in
	// the given direction is covering the in-plane neighbor, but its side faces into the open part of the partial block
	// and meets this face at the corner, so it applies the overlay in place of the in-plane neighbor. This matches the
	// overlay the partial block's own inset face receives from that block. Corners with nothing solid in front, and
	// sources above or below the partial block, are left alone so walls standing on an overlay source are unaffected.
	protected boolean appliesOverlayInsideCorner(Direction dir, BlockPos.MutableBlockPos mutablePos, BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, Direction lightFace, TextureAtlasSprite quadSprite) {
		if (!dir.getAxis().isHorizontal()) {
			return false;
		}
		mutablePos.setWithOffset(pos, lightFace);
		BlockState frontState = level.getBlockState(mutablePos);
		if (frontState.getCollisionShape(level, mutablePos).isEmpty()) {
			return false;
		}
		mutablePos.move(dir);
		BlockState otherState = level.getBlockState(mutablePos);
		Direction otherFace = dir.getOpposite();
		if (!Block.shouldRenderFace(otherState, frontState, otherFace)) {
			return false;
		}
		BlockState otherAppearanceState = otherState.getAppearance(level, mutablePos, otherFace, state, pos);
		return appliesOverlay(mutablePos, otherAppearanceState, otherState, level, pos, appearanceState, state, otherFace, quadSprite);
	}

	protected SpriteCollector fromTwoSidesAdj(SpriteCollector collector, @Nullable BlockState appearanceState0, @Nullable BlockState appearanceState1, Direction dir0, Direction dir1, int sprite, int spriteC01, BlockPos.MutableBlockPos mutablePos, BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, Direction lightFace, TextureAtlasSprite quadSprite, boolean onBoundary) {
		collector.add(sprites[sprite]);
		// OptiFine does not check whether the other two adjacent blocks have the same overlay before trying to apply
		// the corner overlay. I consider this a bug since it is inconsistent with other cases, so it is fixed here by
		// checking those blocks.
		if ((hasSameOverlay(appearanceState0, lightFace)
				|| hasSameOverlay(appearanceState1, lightFace))
				&& appliesOverlayCorner(dir0, dir1, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary)) {
			collector.add(sprites[spriteC01]);
		}
		return collector;
	}

	protected SpriteCollector fromOneSide(SpriteCollector collector, @Nullable BlockState appearanceState0, @Nullable BlockState appearanceState1, @Nullable BlockState appearanceState2, Direction dir0, Direction dir1, Direction dir2, int sprite, int spriteC01, int spriteC12, BlockPos.MutableBlockPos mutablePos, BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, Direction lightFace, TextureAtlasSprite quadSprite, boolean onBoundary) {
		boolean c01;
		boolean c12;
		if (hasSameOverlay(appearanceState1, lightFace)) {
			c01 = true;
			c12 = true;
		} else {
			c01 = hasSameOverlay(appearanceState0, lightFace);
			c12 = hasSameOverlay(appearanceState2, lightFace);
		}

		collector.add(sprites[sprite]);
		if (c01 && appliesOverlayCorner(dir0, dir1, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary)) {
			collector.add(sprites[spriteC01]);
		}
		if (c12 && appliesOverlayCorner(dir1, dir2, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary)) {
			collector.add(sprites[spriteC12]);
		}
		return collector;
	}

	protected static SpriteCollector getCollector(ProcessingDataProvider dataProvider) {
		return dataProvider.getData(ProcessingDataKeys.SPRITE_COLLECTOR);
	}

	protected SpriteCollector prepareCollector(SpriteCollector collector, int sprite0) {
		collector.add(sprites[sprite0]);
		return collector;
	}

	protected SpriteCollector prepareCollector(SpriteCollector collector, int sprite0, int sprite1) {
		collector.add(sprites[sprite0]);
		collector.add(sprites[sprite1]);
		return collector;
	}

	/*
	0:	CORNER D+R
	1:	D
	2:	CORNER L+D
	3:	D R
	4:	L D
	5:	L D R
	6:	L D U
	7:	R
	8:	L D R U
	9:	L
	10:	R U
	11:	L U
	12:	D R U
	13:	L R U
	14:	CORNER R+U
	15:	U
	16:	CORNER L+U
	 */
	@Nullable
	protected SpriteCollector getSprites(BlockAndTintGetter level, BlockPos pos, BlockState appearanceState, BlockState state, Direction lightFace, TextureAtlasSprite quadSprite, QuadView quad, Direction[] directions, ProcessingDataProvider dataProvider) {
		BlockPos.MutableBlockPos mutablePos = dataProvider.getData(ProcessingDataKeys.MUTABLE_POS);
		boolean onBoundary = QuadUtil.isQuadOnBlockBoundary(quad);

		// neighbor is normally skipped when the block in front of it is solid, because it hides/culls the neighbor next to
		// this face. A face inset from the block boundary, such as the top of a bottom slab, instead sees the side of
		// the neighbor between the face and the boundary, which that block does not hide, so the check only applies to
		// faces on the boundary.

		// [up] | [right] | [down] | [left]
		//     8
		// 1   *   4
		//     2
		int applications = 0;

		mutablePos.setWithOffset(pos, directions[0]).move(lightFace);
		BlockState appearanceState0;
		if (!onBoundary || !level.getBlockState(mutablePos).isSolidRender()) {
			mutablePos.setWithOffset(pos, directions[0]);
			BlockState state0 = level.getBlockState(mutablePos);
			appearanceState0 = state0.getAppearance(level, mutablePos, lightFace, state, pos);
			if (appliesOverlayAlongEdge(quad, directions[0], mutablePos, appearanceState0, state0, level, pos, appearanceState, state, lightFace, quadSprite)) {
				applications |= 0b0001;
			}
		} else {
			appearanceState0 = null;
			if (appliesOverlayInsideCorner(directions[0], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite)) {
				applications |= 0b0001;
			}
		}

		mutablePos.setWithOffset(pos, directions[1]).move(lightFace);
		BlockState appearanceState1;
		if (!onBoundary || !level.getBlockState(mutablePos).isSolidRender()) {
			mutablePos.setWithOffset(pos, directions[1]);
			BlockState state1 = level.getBlockState(mutablePos);
			appearanceState1 = state1.getAppearance(level, mutablePos, lightFace, state, pos);
			if (appliesOverlayAlongEdge(quad, directions[1], mutablePos, appearanceState1, state1, level, pos, appearanceState, state, lightFace, quadSprite)) {
				applications |= 0b0010;
			}
		} else {
			appearanceState1 = null;
			if (appliesOverlayInsideCorner(directions[1], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite)) {
				applications |= 0b0010;
			}
		}

		mutablePos.setWithOffset(pos, directions[2]).move(lightFace);
		BlockState appearanceState2;
		if (!onBoundary || !level.getBlockState(mutablePos).isSolidRender()) {
			mutablePos.setWithOffset(pos, directions[2]);
			BlockState state2 = level.getBlockState(mutablePos);
			appearanceState2 = state2.getAppearance(level, mutablePos, lightFace, state, pos);
			if (appliesOverlayAlongEdge(quad, directions[2], mutablePos, appearanceState2, state2, level, pos, appearanceState, state, lightFace, quadSprite)) {
				applications |= 0b0100;
			}
		} else {
			appearanceState2 = null;
			if (appliesOverlayInsideCorner(directions[2], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite)) {
				applications |= 0b0100;
			}
		}

		mutablePos.setWithOffset(pos, directions[3]).move(lightFace);
		BlockState appearanceState3;
		if (!onBoundary || !level.getBlockState(mutablePos).isSolidRender()) {
			mutablePos.setWithOffset(pos, directions[3]);
			BlockState state3 = level.getBlockState(mutablePos);
			appearanceState3 = state3.getAppearance(level, mutablePos, lightFace, state, pos);
			if (appliesOverlayAlongEdge(quad, directions[3], mutablePos, appearanceState3, state3, level, pos, appearanceState, state, lightFace, quadSprite)) {
				applications |= 0b1000;
			}
		} else {
			appearanceState3 = null;
			if (appliesOverlayInsideCorner(directions[3], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite)) {
				applications |= 0b1000;
			}
		}

		return switch (applications) {
			case 0b1111 -> prepareCollector(getCollector(dataProvider), 8);
			case 0b0111 -> prepareCollector(getCollector(dataProvider), 5);
			case 0b1011 -> prepareCollector(getCollector(dataProvider), 6);
			case 0b1101 -> prepareCollector(getCollector(dataProvider), 13);
			case 0b1110 -> prepareCollector(getCollector(dataProvider), 12);
			//
			case 0b0101 -> prepareCollector(getCollector(dataProvider), 9, 7);
			case 0b1010 -> prepareCollector(getCollector(dataProvider), 1, 15);
			//
			case 0b0011 -> fromTwoSidesAdj(getCollector(dataProvider), appearanceState2, appearanceState3, directions[2], directions[3], 4, 14, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);
			case 0b0110 -> fromTwoSidesAdj(getCollector(dataProvider), appearanceState3, appearanceState0, directions[3], directions[0], 3, 16, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);
			case 0b1100 -> fromTwoSidesAdj(getCollector(dataProvider), appearanceState0, appearanceState1, directions[0], directions[1], 10, 2, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);
			case 0b1001 -> fromTwoSidesAdj(getCollector(dataProvider), appearanceState1, appearanceState2, directions[1], directions[2], 11, 0, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);
			//
			case 0b0001 -> fromOneSide(getCollector(dataProvider), appearanceState1, appearanceState2, appearanceState3, directions[1], directions[2], directions[3], 9, 0, 14, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);
			case 0b0010 -> fromOneSide(getCollector(dataProvider), appearanceState2, appearanceState3, appearanceState0, directions[2], directions[3], directions[0], 1, 14, 16, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);
			case 0b0100 -> fromOneSide(getCollector(dataProvider), appearanceState3, appearanceState0, appearanceState1, directions[3], directions[0], directions[1], 7, 16, 2, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);
			case 0b1000 -> fromOneSide(getCollector(dataProvider), appearanceState0, appearanceState1, appearanceState2, directions[0], directions[1], directions[2], 15, 2, 0, mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);
			//
			case 0b0000 -> {
				boolean s0 = hasSameOverlay(appearanceState0, lightFace);
				boolean s1 = hasSameOverlay(appearanceState1, lightFace);
				boolean s2 = hasSameOverlay(appearanceState2, lightFace);
				boolean s3 = hasSameOverlay(appearanceState3, lightFace);

				boolean c01 = (s0 | s1) && appliesOverlayCorner(directions[0], directions[1], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);
				boolean c12 = (s1 | s2) && appliesOverlayCorner(directions[1], directions[2], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);
				boolean c23 = (s2 | s3) && appliesOverlayCorner(directions[2], directions[3], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);
				boolean c30 = (s3 | s0) && appliesOverlayCorner(directions[3], directions[0], mutablePos, level, pos, appearanceState, state, lightFace, quadSprite, onBoundary);

				if (c01 | c12 | c23 | c30) {
					SpriteCollector collector = getCollector(dataProvider);
					if (c01) {
						collector.add(sprites[2]);
					}
					if (c12) {
						collector.add(sprites[0]);
					}
					if (c23) {
						collector.add(sprites[14]);
					}
					if (c30) {
						collector.add(sprites[16]);
					}
					yield collector;
				}

				yield null;
			}
			//
			default -> throw new IllegalStateException("Unexpected value: " + applications);
		};
	}

	public static class SpriteCollector {
		protected static final TextureAtlasSprite[] EMPTY_SPRITES = new TextureAtlasSprite[4];

		protected TextureAtlasSprite[] sprites = new TextureAtlasSprite[4];
		protected int spriteAmount;

		public void add(@Nullable TextureAtlasSprite sprite) {
			if (sprite != null) {
				sprites[spriteAmount++] = sprite;
			}
		}

		public void clear() {
			System.arraycopy(EMPTY_SPRITES, 0, sprites, 0, EMPTY_SPRITES.length);
			spriteAmount = 0;
		}
	}

	public static class Factory extends AbstractQuadProcessorFactory<StandardOverlayCtmProperties> {
		@Override
		public QuadProcessor createProcessor(StandardOverlayCtmProperties properties, TextureAtlasSprite[] sprites) {
			OverlayPropertiesSection overlaySection = properties.getOverlayPropertiesSection();
			return new StandardOverlayQuadProcessor(sprites, OverlayProcessingPredicate.fromProperties(properties), properties.getMatchTilesSet(), properties.getMatchBlocksPredicate(), properties.getConnectTilesSet(), properties.getConnectBlocksPredicate(), properties.getConnectionPredicate(), overlaySection.getTintIndex(), overlaySection.getTintBlock(), overlaySection.getLayer());
		}

		@Override
		public int getSpriteAmount(StandardOverlayCtmProperties properties) {
			return 17;
		}

		@Override
		public boolean supportsNullSprites(StandardOverlayCtmProperties properties) {
			return false;
		}
	}
}
