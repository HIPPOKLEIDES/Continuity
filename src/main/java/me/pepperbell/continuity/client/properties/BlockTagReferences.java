package me.pepperbell.continuity.client.properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.pepperbell.continuity.client.ContinuityClient;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block tags referenced by our Optifine/Cotinuity properties files. The issue: tags only exist once a world is loaded,
 * long after properties files are parsed, so a reference to a tag that does not exist would otherwise quietly match with nothing.
 * References are checked whenever tags load, and after a resource reload while a world is loaded. Each problem is logged once per resource
 * reload.
 */
public final class BlockTagReferences {
	private static final List<Reference> PENDING = Collections.synchronizedList(new ArrayList<>());
	private static volatile List<Reference> active = List.of();
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	static void add(TagKey<Block> tag, Map<String, ObjectOpenHashSet<String>> filters, String element, int index, String propertyKey, Identifier fileLocation, String packId) {
		PENDING.add(new Reference(tag, filters, element, index, propertyKey, fileLocation, packId));
	}

	// Called when a resource reload starts, before any properties files are parsed.
	public static void startReload() {
		PENDING.clear();
	}

	// Called when a resource reload is applied. Tags are already loaded if a world is loaded, so check right away.
	public static void finishReload(boolean worldLoaded) {
		synchronized (PENDING) {
			active = List.copyOf(PENDING);
			PENDING.clear();
		}
		WARNED.clear();
		if (worldLoaded) {
			validate(BuiltInRegistries.BLOCK);
		}
	}

	public static void onTagsLoaded(RegistryAccess registries) {
		validate(registries.lookupOrThrow(Registries.BLOCK));
	}

	private static void validate(Registry<Block> registry) {
		for (Reference reference : active) {
			String problem = reference.findProblem(registry);
			if (problem != null) {
				String message = problem + " in '" + reference.propertyKey + "' element '" + reference.element + "' at index " + reference.index + " in file '" + reference.fileLocation + "' in pack '" + reference.packId + "'";
				if (WARNED.add(message)) {
					ContinuityClient.LOGGER.warn(message);
				}
			}
		}
	}

	private record Reference(TagKey<Block> tag, Map<String, ObjectOpenHashSet<String>> filters, String element, int index, String propertyKey, Identifier fileLocation, String packId) {
		@Nullable
		String findProblem(Registry<Block> registry) {
			Optional<HolderSet.Named<Block>> blocks = registry.get(tag);
			if (blocks.isEmpty()) {
				return "Unknown block tag '#" + tag.location() + "'";
			}
			for (Holder<Block> holder : blocks.get()) {
				if (filters.isEmpty()) {
					return null;
				}
				for (BlockState state : holder.value().getStateDefinition().getPossibleStates()) {
					if (PropertiesParsingHelper.matchesFilters(state, filters)) {
						return null;
					}
				}
			}
			return filters.isEmpty() ? "Block tag '#" + tag.location() + "' is empty" : "No block in block tag '#" + tag.location() + "' matches the property filters";
		}
	}
}
