package parallelmc.parallelutils.modules.parallelparkour;

import org.bukkit.Location;
import java.util.List;

public record ParkourLayout(String name, List<Location> positions, boolean allowEffects) {
    public void addLocation(Location location) {
        positions.add(location);
    }
}