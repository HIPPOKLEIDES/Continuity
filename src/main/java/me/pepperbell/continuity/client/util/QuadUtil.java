package me.pepperbell.continuity.client.util;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import me.pepperbell.continuity.client.mixinterface.TextureAtlasSpriteExtension;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.model.MeshQuadCollection;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinderGetter;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class QuadUtil {
	public static void interpolate(MutableQuadView quad, TextureAtlasSprite oldSprite, TextureAtlasSprite newSprite) {
		float oldMinU = oldSprite.getU0();
		float oldMinV = oldSprite.getV0();
		float newMinU = newSprite.getU0();
		float newMinV = newSprite.getV0();
		float uFactor = (newSprite.getU1() - newMinU) / (oldSprite.getU1() - oldMinU);
		float vFactor = (newSprite.getV1() - newMinV) / (oldSprite.getV1() - oldMinV);
		for (int i = 0; i < 4; i++) {
			quad.uv(i,
					newMinU + (quad.u(i) - oldMinU) * uFactor,
					newMinV + (quad.v(i) - oldMinV) * vFactor
			);
		}
		quad.animated(newSprite.contents().isAnimated());
	}

	public static void interpolate(BakedQuad quad, PackedUvContainer output, TextureAtlasSprite oldSprite, TextureAtlasSprite newSprite) {
		float oldMinU = oldSprite.getU0();
		float oldMinV = oldSprite.getV0();
		float newMinU = newSprite.getU0();
		float newMinV = newSprite.getV0();
		float uFactor = (newSprite.getU1() - newMinU) / (oldSprite.getU1() - oldMinU);
		float vFactor = (newSprite.getV1() - newMinV) / (oldSprite.getV1() - oldMinV);
		for (int i = 0; i < 4; i++) {
			long packedUv = quad.packedUV(i);
			output.packedUV(i, UVPair.pack(
					newMinU + (UVPair.unpackU(packedUv) - oldMinU) * uFactor,
					newMinV + (UVPair.unpackV(packedUv) - oldMinV) * vFactor
			));
		}
	}

	/**
	 * Emits an overlay quad with the same geometry as the given base quad. The overlay sprite is mapped onto the block
	 * face from the vertex positions, so it stays flush with faces of the block boundary (slab and
	 * layer tops) and is clipped to faces that only cover part of the block face (slab sides, stair steps).
	 */
	public static void emitOverlayQuad(QuadEmitter emitter, QuadView quad, TextureAtlasSprite sprite, int color, ChunkSectionLayer chunkLayer, TriState ao) {
		Direction face = quad.lightFace();
		float minU = sprite.getU0();
		float minV = sprite.getV0();
		float uRange = sprite.getU1() - minU;
		float vRange = sprite.getV1() - minV;
		for (int i = 0; i < 4; i++) {
			float x = quad.x(i);
			float y = quad.y(i);
			float z = quad.z(i);
			emitter.pos(i, x, y, z);
			emitter.uv(i, minU + getFaceU(face, x, z) * uRange, minV + getFaceV(face, y, z) * vRange);
		}
		emitter.cullFace(quad.cullFace());
		emitter.nominalFace(face);
		emitter.color(color, color, color, color);
		emitter.atlas(QuadAtlas.BLOCK);
		emitter.animated(sprite.contents().isAnimated());
		emitter.chunkLayer(chunkLayer);
		emitter.itemRenderType(chunkLayer == ChunkSectionLayer.TRANSLUCENT ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet());
		emitter.ambientOcclusion(ao);
		emitter.emit();
	}

	// Horizontal texture coordinate of a point on the given face, in [0, 1] from the face's left edge. Uses the same
	// layout as MutableQuadView#square, which matches the texture left/up directions in DirectionMaps.
	private static float getFaceU(Direction face, float x, float z) {
		return switch (face) {
			case DOWN, UP, SOUTH -> x;
			case NORTH -> 1 - x;
			case WEST -> z;
			case EAST -> 1 - z;
		};
	}

	// Vertical texture coordinate of a point on the given face, in [0, 1] from the face's top edge.
	private static float getFaceV(Direction face, float y, float z) {
		return switch (face) {
			case DOWN -> 1 - z;
			case UP -> z;
			default -> 1 - y;
		};
	}

	/**
	 * Returns whether the given quad lies in a plane perpendicular to its light face and within the block bounds, which
	 * is required to project a block face texture onto it. The quad does not need to cover the whole face or lie on the
	 * block boundary.
	 */
	public static boolean isQuadFlatInBlock(QuadView quad) {
		int depthIndex = getAxisIndex(quad.lightFace().getAxis());
		float depth = quad.posByIndex(0, depthIndex);
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 3; j++) {
				float p = quad.posByIndex(i, j);
				if (p <= -0.0001f || p >= 1.0001f) {
					return false;
				}
			}
			float d = quad.posByIndex(i, depthIndex);
			if (d >= depth + 0.0001f || d <= depth - 0.0001f) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns whether the given quad lies on the block boundary in the direction of its light face, rather than being
	 * inset like the top of a bottom slab. Only checks the first vertex, so the quad must already be known to be flat.
	 */
	public static boolean isQuadOnBlockBoundary(QuadView quad) {
		Direction face = quad.lightFace();
		float depth = quad.posByIndex(0, getAxisIndex(face.getAxis()));
		float boundary = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
		return depth < boundary + 0.0001f && depth > boundary - 0.0001f;
	}

	/**
	 * Returns whether the shape of the neighbor in the given in-plane direction borders the whole edge of the given flat
	 * quad on that side. The neighbor's shape must touch the edge either level with the quad (just behind its plane) or
	 * rising from it (just in front of its plane), and the quad must reach that side of the block. The shape is in the
	 * neighbor's block space, which only differs from the quad's along the direction's axis.
	 */
	public static boolean isQuadEdgeBorderedBy(QuadView quad, Direction dir, VoxelShape neighborShape) {
		Direction face = quad.lightFace();
		int depthIndex = getAxisIndex(face.getAxis());
		int dirIndex = getAxisIndex(dir.getAxis());
		int edgeIndex = 3 - depthIndex - dirIndex;

		float dirMin = Float.POSITIVE_INFINITY;
		float dirMax = Float.NEGATIVE_INFINITY;
		float edgeMin = Float.POSITIVE_INFINITY;
		float edgeMax = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < 4; i++) {
			float d = quad.posByIndex(i, dirIndex);
			dirMin = Math.min(dirMin, d);
			dirMax = Math.max(dirMax, d);
			float e = quad.posByIndex(i, edgeIndex);
			edgeMin = Math.min(edgeMin, e);
			edgeMax = Math.max(edgeMax, e);
		}
		boolean reachesSide = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? dirMax > 0.9999f : dirMin < 0.0001f;
		if (!reachesSide || edgeMax - edgeMin < 0.002f) {
			return false;
		}

		VoxelShape side = neighborShape.getFaceShape(dir.getOpposite());
		if (side.isEmpty()) {
			return false;
		}

		// Keep the tested strips slightly away from the quad's plane and the edge's ends so that shape boundaries
		// that line up with them exactly still count as touching.
		double depth = quad.posByIndex(0, depthIndex);
		double behind = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? -1 : 1;
		return coversStrip(side, depthIndex, edgeIndex, edgeMin + 0.001, edgeMax - 0.001, depth + behind * 0.0001, depth + behind * 0.01)
				|| coversStrip(side, depthIndex, edgeIndex, edgeMin + 0.001, edgeMax - 0.001, depth - behind * 0.0001, depth - behind * 0.01);
	}

	// Whether the shape covers the strip spanning the given range along the edge axis, the range between the given
	// depths along the depth axis and the whole block along the remaining axis.
	private static boolean coversStrip(VoxelShape shape, int depthIndex, int edgeIndex, double edgeMin, double edgeMax, double depthA, double depthB) {
		double[] min = { 0, 0, 0 };
		double[] max = { 1, 1, 1 };
		min[depthIndex] = Math.min(depthA, depthB);
		max[depthIndex] = Math.max(depthA, depthB);
		min[edgeIndex] = edgeMin;
		max[edgeIndex] = edgeMax;
		VoxelShape strip = Shapes.box(min[0], min[1], min[2], max[0], max[1], max[2]);
		return !Shapes.joinIsNotEmpty(strip, shape, BooleanOp.ONLY_FIRST);
	}

	private static int getAxisIndex(Direction.Axis axis) {
		return switch (axis) {
			case X -> 0;
			case Y -> 1;
			case Z -> 2;
		};
	}

	/**
	 * Returns an int in range [0, 7] representing the texture orientation of the given quad relative to the world.
	 *
	 * <ul>
	 *     <li>0 - 0 degree counterclockwise rotation, counterclockwise UV winding order</li>
	 *     <li>1 - 90 degree counterclockwise rotation, counterclockwise UV winding order</li>
	 *     <li>2 - 180 degree counterclockwise rotation, counterclockwise UV winding order</li>
	 *     <li>3 - 270 degree counterclockwise rotation, counterclockwise UV winding order</li>
	 *     <li>4 - 0 degree counterclockwise rotation, clockwise UV winding order</li>
	 *     <li>5 - 90 degree counterclockwise rotation, clockwise UV winding order</li>
	 *     <li>6 - 180 degree counterclockwise rotation, clockwise UV winding order</li>
	 *     <li>7 - 270 degree counterclockwise rotation, clockwise UV winding order</li>
	 * </ul>
	 */
	public static int getTextureOrientation(QuadView quad) {
		// Texture matrix
		float tm00 = quad.u(3) - quad.u(1);
		float tm01 = quad.v(3) - quad.v(1);
		float tm10 = quad.u(2) - quad.u(0);
		float tm11 = quad.v(2) - quad.v(0);
		// Determinant of texture matrix; also cross product of its column vectors
		float determinant = tm00 * tm11 - tm10 * tm01;
		if (determinant == 0) {
			return 0;
		}
		float s = 1 / determinant;
		// Second column of inverse texture matrix
		float itm10 = -tm10 * s;
		float itm11 = tm00 * s;

		int xAxis;
		int xAxisSign;
		int yAxis;
		int yAxisSign;
		switch (quad.lightFace()) {
			case DOWN -> {
				xAxis = 0; // +X
				xAxisSign = 1;
				yAxis = 2; // +Z
				yAxisSign = 1;
			}
			case UP -> {
				xAxis = 0; // +X
				xAxisSign = 1;
				yAxis = 2; // -Z
				yAxisSign = -1;
			}
			case NORTH -> {
				xAxis = 0; // -X
				xAxisSign = -1;
				yAxis = 1; // +Y
				yAxisSign = 1;
			}
			case SOUTH -> {
				xAxis = 0; // +X
				xAxisSign = 1;
				yAxis = 1; // +Y
				yAxisSign = 1;
			}
			case WEST -> {
				xAxis = 2; // +Z
				xAxisSign = 1;
				yAxis = 1; // +Y
				yAxisSign = 1;
			}
			case EAST -> {
				xAxis = 2; // -Z
				xAxisSign = -1;
				yAxis = 1; // +Y
				yAxisSign = 1;
			}
			default -> {
				return 0;
			}
		}
		// Position matrix
		float pm00 = quad.posByIndex(3, xAxis) - quad.posByIndex(1, xAxis);
		float pm01 = quad.posByIndex(3, yAxis) - quad.posByIndex(1, yAxis);
		float pm10 = quad.posByIndex(2, xAxis) - quad.posByIndex(0, xAxis);
		float pm11 = quad.posByIndex(2, yAxis) - quad.posByIndex(0, yAxis);

		// Texture up vector in projected world space
		// Computed as (position matrix * inverse texture matrix * [0; -1]); [0; -1] is the texture up vector in texture space
		// Axis signs should be multiplied into position matrix values, but multiplying here instead saves 2 multiplications
		float x = -(pm00 * itm10 + pm10 * itm11) * xAxisSign;
		float y = -(pm01 * itm10 + pm11 * itm11) * yAxisSign;

		// Clamp vector to nearest axis-aligned direction
		// up/+y -> 0, left/-x -> 1, down/-y -> 2, right/+x -> 3
		// Add 4 if the UV winding order is clockwise
		return (Math.abs(y) >= Math.abs(x) ? (y > 0 ? 0 : 2) : (x > 0 ? 3 : 1)) + (determinant < 0 ? 4 : 0);
	}

	@Nullable
	public static QuadCollection createEmissiveQuads(QuadCollection quads, @Nullable SpriteFinderGetter spriteFinderGetter) {
		if (quads instanceof MeshQuadCollection meshQuadCollection) {
			if (spriteFinderGetter == null) {
				return null;
			}

			Mesh emissiveMesh = createEmissiveMesh(meshQuadCollection.getMesh(), spriteFinderGetter);
			return emissiveMesh != null ? new MeshQuadCollection(emissiveMesh) : null;
		}

		QuadCollection.Builder emissiveQuadsBuilder = null;
		PackedUvContainer output = null;
		for (BakedQuad quad : quads.getAll()) {
			BakedQuad.MaterialInfo materialInfo = quad.materialInfo();
			TextureAtlasSprite emissiveSprite = ((TextureAtlasSpriteExtension) materialInfo.sprite()).continuity$getEmissiveSprite();
			if (emissiveSprite != null) {
				if (emissiveQuadsBuilder == null) {
					output = new PackedUvContainer();
					emissiveQuadsBuilder = new QuadCollection.Builder();
				}

				interpolate(quad, output, quad.materialInfo().sprite(), emissiveSprite);
				BakedQuad.MaterialInfo emissiveMaterialInfo = new BakedQuad.MaterialInfo(emissiveSprite, materialInfo.layer(), materialInfo.itemRenderType(), materialInfo.tintIndex(), false, 15);
				BakedQuad emissiveQuad = new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(), output.packedUV0, output.packedUV1, output.packedUV2, output.packedUV3, quad.direction(), emissiveMaterialInfo);
				emissiveQuadsBuilder.addUnculledFace(emissiveQuad);
			}
		}
		return emissiveQuadsBuilder != null ? emissiveQuadsBuilder.build() : null;
	}

	@Nullable
	public static Mesh createEmissiveMesh(MeshView mesh, SpriteFinderGetter spriteFinderGetter) {
		var quadConsumer = new Consumer<QuadView>() {
			@Nullable
			private MutableMesh emissiveMeshBuilder;
			@Nullable
			private QuadEmitter emitter;

			@Override
			public void accept(QuadView quad) {
				TextureAtlasSprite sprite = spriteFinderGetter.spriteFinder(quad.atlas()).find(quad);
				TextureAtlasSprite emissiveSprite = ((TextureAtlasSpriteExtension) sprite).continuity$getEmissiveSprite();
				if (emissiveSprite != null) {
					if (emissiveMeshBuilder == null) {
						emissiveMeshBuilder = Renderer.get().mutableMesh();
						emitter = emissiveMeshBuilder.emitter();
					}

					emitter.copyFrom(quad);
					interpolate(emitter, sprite, emissiveSprite);
					emitter.emissive(true);
					emitter.emit();
				}
			}
		};
		mesh.forEach(quadConsumer);
		return quadConsumer.emissiveMeshBuilder != null ? quadConsumer.emissiveMeshBuilder.immutableCopy() : null;
	}

	public static class PackedUvContainer {
		public long packedUV0;
		public long packedUV1;
		public long packedUV2;
		public long packedUV3;

		public void packedUV(int index, long packedUV) {
			switch (index) {
				case 0 -> packedUV0 = packedUV;
				case 1 -> packedUV1 = packedUV;
				case 2 -> packedUV2 = packedUV;
				case 3 -> packedUV3 = packedUV;
				default -> throw new IndexOutOfBoundsException(index);
			}
		}
	}
}
