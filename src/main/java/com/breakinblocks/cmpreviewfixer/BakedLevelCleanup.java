package com.breakinblocks.cmpreviewfixer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class BakedLevelCleanup {
    private static final Logger LOG = CMPreviewFixer.LOG;

    private static final String F_RENDERER = "renderer";
    private static final String F_BAKED_LEVEL = "bakedLevel";
    private static final String F_STATE = "state";
    private static final String F_PROPERTIES = "properties";
    private static final String F_INTERMEDIARY_COPY_TARGET = "intermediaryCopyTarget";
    private static final String M_SECTIONS = "sections";
    private static final String M_BLOCK_BUFFERS = "blockBuffers";
    private static final String M_FLUID_BUFFERS = "fluidBuffers";
    private static final String M_BLOCK_BUILDERS = "blockBuilders";
    private static final String M_FLUID_BUILDERS = "fluidBuilders";

    private static final String CLS_TRANSLUCENCY_CHAIN = "dev.compactmods.gander.render.translucency.TranslucencyChain";
    private static final String CLS_RENDER_TYPE_STORE = "dev.compactmods.gander.render.rendertypes.RenderTypeStore";

    private BakedLevelCleanup() {}

    public static void disposeScreenRenderer(Object screen) {
        if (screen == null) return;
        Object renderer = readField(screen, F_RENDERER);
        if (renderer == null) return;
        Object bakedLevel = readField(renderer, F_BAKED_LEVEL);
        Object pipelineState = readField(renderer, F_STATE);
        if (bakedLevel == null && pipelineState == null) return;
        scheduleDispose(bakedLevel, pipelineState);
    }

    private static void scheduleDispose(Object bakedLevel, Object pipelineState) {
        Runnable task = () -> {
            if (bakedLevel != null) {
                try {
                    disposeBakedLevel(bakedLevel);
                } catch (Throwable t) {
                    LOG.warn("CMPreviewFixer: failed to dispose BakedLevel", t);
                }
            }
            if (pipelineState != null) {
                try {
                    disposePipelineState(pipelineState);
                } catch (Throwable t) {
                    LOG.warn("CMPreviewFixer: failed to dispose PipelineState", t);
                }
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

    private static void disposePipelineState(Object pipelineState) {
        Object propertiesObj = readField(pipelineState, F_PROPERTIES);
        if (!(propertiesObj instanceof Map<?, ?> properties)) return;
        for (Object value : properties.values()) {
            disposeStateValue(value);
        }
        try {
            Method clear = findMethod(pipelineState.getClass(), "clear");
            if (clear != null) clear.invoke(pipelineState);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void disposeStateValue(Object value) {
        if (value == null) return;
        if (value instanceof RenderTarget rt) {
            try {
                rt.destroyBuffers();
            } catch (Throwable ignored) {
            }
            return;
        }
        String cls = value.getClass().getName();
        if (cls.equals(CLS_TRANSLUCENCY_CHAIN)) {
            // TranslucencyChain.close() leaves intermediaryCopyTarget allocated.
            Object intermediary = readField(value, F_INTERMEDIARY_COPY_TARGET);
            if (intermediary instanceof RenderTarget rt) {
                try {
                    rt.destroyBuffers();
                } catch (Throwable ignored) {
                }
            }
            if (value instanceof AutoCloseable ac) {
                try {
                    ac.close();
                } catch (Throwable ignored) {
                }
            }
        } else if (cls.equals(CLS_RENDER_TYPE_STORE)) {
            invokeNoArg(value, "dispose");
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
