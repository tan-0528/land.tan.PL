package tan0528.land;

import org.bukkit.Location;
import java.util.UUID;

public class Plot {
    public String id;
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public UUID owner;

    public Plot(String id, Location l1, Location l2) {
        this.id = id;
        this.minX = Math.min(l1.getBlockX(), l2.getBlockX());
        this.minY = Math.min(l1.getBlockY(), l2.getBlockY());
        this.minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        this.maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        this.maxY = Math.max(l1.getBlockY(), l2.getBlockY());
        this.maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());
        this.owner = null; // 初期状態は持ち主なし
    }
}
