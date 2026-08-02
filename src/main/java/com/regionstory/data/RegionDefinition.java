package com.regionstory.data;

import net.minecraft.util.Identifier;

public final class RegionDefinition {
    public enum Type { SPHERE, BOX }
    public final String id;
    public final Identifier dimension;
    public final Type type;
    public final double[] center;
    public final double radius;
    public final double[] min;
    public final double[] max;
    public final String prompt;
    public final String icon;
    public final String dialogue;
    public final int priority;

    public RegionDefinition(String id, Identifier dimension, Type type, double[] center, double radius,
                             double[] min, double[] max, String prompt, String icon, String dialogue, int priority) {
        this.id = id; this.dimension = dimension; this.type = type; this.center = center.clone(); this.radius = radius;
        this.min = new double[]{Math.min(min[0], max[0]), Math.min(min[1], max[1]), Math.min(min[2], max[2])};
        this.max = new double[]{Math.max(min[0], max[0]), Math.max(min[1], max[1]), Math.max(min[2], max[2])};
        this.prompt = prompt; this.icon = icon; this.dialogue = dialogue; this.priority = priority;
    }

    public boolean contains(Identifier currentDimension, double x, double y, double z) {
        if (!dimension.equals(currentDimension)) return false;
        if (type == Type.SPHERE) {
            double dx = x - center[0], dy = y - center[1], dz = z - center[2];
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }
        return x >= min[0] && x <= max[0] && y >= min[1] && y <= max[1] && z >= min[2] && z <= max[2];
    }
}
