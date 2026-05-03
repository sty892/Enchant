package me.sty892.enchant.event.boss;

public class BossEventConfig {
    public String boss_id;
    public BossEvent on_spawn;
    public BossEvent on_death;

    public static class BossEvent {
        public String play_animation;
        public WorldBorderExpand world_border_expand;
        public String spawn_structure;
        public Offset spawn_structure_offset;
        public String give_fragment;
        public String set_flag;
        public String broadcast_title;
        public boolean allow_diamonds;
    }

    public static class WorldBorderExpand {
        public double to;
        public int duration_seconds;
    }

    public static class Offset {
        public int x, y, z;
    }
}
