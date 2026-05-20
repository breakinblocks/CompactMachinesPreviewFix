package com.breakinblocks.cmpreviewfixer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Reflective disposer for Gander's BakedLevel / SpatialRenderer graph.
 *
 * Gander allocates a SectionBufferBuilderPack pair (~10-20 MB of off-heap
 * SectionBufferBuilder buffers) and a VertexBuffer per RenderType for every
 * section of the previewed room, but never releases them. With a 45 cubed
 * room that adds up to several hundred megabytes of committed native memory
 * per preview open, accumulating until the JVM is OOM-killed.
 *
 * We can't depend on Gander at compile time (only on a credentialed Maven),
 * so the cleanup walks the record accessors by name. VertexBuffer.close()
 * must run on the render thread.
 */
public final class BakedLevelCleanup {
    private static final Logger LOG = CMPreviewFixer.LOG;

    private static final String F_RENDERER = "renderer";
    private static final String F_BAKED_LEVEL = "bakedLevel";
    private static final String M_SECTIONS = "sections";
    private static final String M_BLOCK_BUFFERS = "blockBuffers";
    private static final String M_FLUID_BUFFERS = "fluidBuffers";
    private static final String M_BLOCK_BUILDERS = "blockBuilders";
    private static final String M_FLUID_BUILDERS = "fluidBuilders";

    private BakedLevelCleanup() {}

    /**
     * Read the SpatialRenderer field on a MachineRoomScreen and dispose its
     * BakedLevel. Safe to call multiple times — null renderers and missing
     * fields are silently skipped.
     */
    public static void disposeScreenRenderer(Object screen) {
        if (screen == null) return;
        Object renderer = readField(screen, F_RENDERER);
        if (renderer == null) return;
        Object bakedLevel = readField(renderer, F_BAKED_LEVEL);
        if (bakedLevel == null) return;
        scheduleDispose(bakedLevel);
    }

    private static void scheduleDispose(Object bakedLevel) {
        Runnable task = () -> {
            try {
                disposeBakedLevel(bakedLevel);
            } catch (Throwable t) {
                LOG.warn("CMPreviewFixer: failed to dispose BakedLevel", t);
            }
        };
        if (RenderSystem.isOnRenderThread()) {
            task.run();
        } else {
            RenderSystem.recordRenderCall(task::run);
        }
    }

    private static void disposeBakedLevel(Object bakedLevel) {
        Object sectionsObj = invokeNoArg(bakedLevel, M_SECTIONS);
        if (!(sectionsObj instanceof Map<?, ?> sections)) return;
        for (Object section : sections.values()) {
            closeBuffers(invokeNoArg(section, M_BLOCK_BUFFERS));
            closeBuffers(invokeNoArg(section, M_FLUID_BUFFERS));
            closePack(invokeNoArg(section, M_BLOCK_BUILDERS));
            closePack(invokeNoArg(section, M_FLUID_BUILDERS));
        }
    }

    private static void closeBuffers(Object maybeMap) {
        if (!(maybeMap instanceof Map<?, ?> map)) return;
        for (Object v : map.values()) {
            if (v instanceof VertexBuffer vb) {
                try {
                    vb.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void closePack(Object maybePack) {
        if (maybePack instanceof SectionBufferBuilderPack pack) {
            try {
                pack.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static Object readField(Object o, String name) {
        try {
            Field f = findField(o.getClass(), name);
            if (f == null) return null;
            return f.get(o);
        } catch (ReflectiveOperationException e) {
            LOG.debug("CMPreviewFixer: readField {} on {} failed: {}", name, o.getClass().getName(), e.toString());
            return null;
        }
    }

    private static Object invokeNoArg(Object o, String name) {
        try {
            Method m = findMethod(o.getClass(), name);
            if (m == null) return null;
            return m.invoke(o);
        } catch (ReflectiveOperationException e) {
            LOG.debug("CMPreviewFixer: invoke {} on {} failed: {}", name, o.getClass().getName(), e.toString());
            return null;
        }
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }
}
