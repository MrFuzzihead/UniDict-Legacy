package com.mrfuzzihead.unidict.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * T2 test for the resource black-list ordering (docs/CODE_REVIEW.md BUG-1). The blacklist used to be
 * applied <em>after</em> the {@link ResourceHandler} indexed the model, so an "excluded" resource was
 * still resolved (and still rewritten) while merely disappearing from the report/NEI view.
 *
 * <p>
 * Drives the extracted seam {@link UniResourceHandler#applyResourceBlackList} and then constructs a
 * {@link ResourceHandler} from the already-filtered map, exactly as {@code postInit()} now does. Only
 * empty {@link Resource} shells are needed — no Ore Dictionary, no containers (T2).
 */
class ResourceBlackListTest {

    /** Builds a model map with empty resource shells (no containers -> no OD access). */
    private static Map<String, Resource<UniResourceContainer>> model(final String... names) {
        final Map<String, Resource<UniResourceContainer>> map = new LinkedHashMap<>();
        for (final String name : names) map.put(name, new Resource<>(name));
        return map;
    }

    @Test
    void blacklistedResourcesAreRemovedBeforeTheHandlerIndexesTheModel() {
        final Map<String, Resource<UniResourceContainer>> resourceMap = model("Copper", "Aluminium", "Tin");
        final Map<String, Resource<UniResourceContainer>> apiResourceMap = model("Copper", "Aluminium", "Tin");

        UniResourceHandler.applyResourceBlackList(resourceMap, apiResourceMap, Arrays.asList("Aluminium"));

        assertFalse(resourceMap.containsKey("Aluminium"), "the unified map must lose the blacklisted resource");
        assertFalse(apiResourceMap.containsKey("Aluminium"), "the api map must lose it too");
        assertTrue(resourceMap.containsKey("Copper"));
        assertTrue(apiResourceMap.containsKey("Tin"));

        // postInit() now filters first and constructs the handler afterwards, so the handler's resource
        // view AND its name lookups (containerExists) reflect the exclusion.
        final ResourceHandler handler = new ResourceHandler(resourceMap);
        assertEquals(2, handler.resources.size());
        assertTrue(handler.resourceExists("Copper"));
        assertFalse(handler.resourceExists("Aluminium"));
        assertFalse(handler.containerExists("Aluminium"));
    }

    @Test
    void unknownNamesAndEmptyBlackListsAreNoOps() {
        final Map<String, Resource<UniResourceContainer>> resourceMap = model("Copper");
        final Map<String, Resource<UniResourceContainer>> apiResourceMap = model("Copper");

        UniResourceHandler.applyResourceBlackList(resourceMap, apiResourceMap, Arrays.asList("NotAMetal"));
        UniResourceHandler.applyResourceBlackList(resourceMap, apiResourceMap, Collections.emptyList());
        UniResourceHandler.applyResourceBlackList(resourceMap, apiResourceMap, null);

        assertEquals(1, resourceMap.size());
        assertEquals(1, apiResourceMap.size());
        assertTrue(resourceMap.containsKey("Copper"));
    }
}
