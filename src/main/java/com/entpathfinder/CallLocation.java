package com.entpathfinder;

import lombok.Getter;

/**
 * Named places a call can be at, so the overlay can say "Nemus Retreat" instead of a coordinate
 * pair that means nothing at a glance.
 * <p>
 * Centres are the world-map positions the OSRS Wiki records for each place. They are the centre of
 * an <em>area</em>, not of its trees -- the Seers' Village maples sit about twenty tiles south of
 * the village pin, and the Nemus Retreat trees about thirty south of theirs -- so matching is
 * nearest-wins within a generous radius rather than an exact hit.
 * <p>
 * Radii overlap in places, which is unavoidable with this many entries and is why the lookup takes
 * the closest rather than the first hit. Because neighbours can be close enough to swap, a cluster
 * resolves its place <em>once</em> and keeps it -- see {@link EventCluster#getLocation()}.
 */
@Getter
public enum CallLocation
{
	// Sailing islands, with wider radii: they are isolated, and the ignore toggles depend on a call
	// out at sea still resolving to the island rather than falling through to nothing.
	SUNBLEAK_ISLAND("Sunbleak Island", 2209, 2330, 120),
	DRUMSTICK_ISLE("Drumstick Isle", 2146, 3545, 120),

	// Kandarin.
	SEERS_VILLAGE("Seers' Village", 2710, 3485),
	CAMELOT("Camelot", 2758, 3507),
	MCGRUBORS_WOOD("McGrubor's Wood", 2652, 3485),
	FISHING_GUILD("Fishing Guild", 2609, 3424),
	CATHERBY("Catherby", 2814, 3443),
	EAST_ARDOUGNE("East Ardougne", 2624, 3300),
	WEST_ARDOUGNE("West Ardougne", 2500, 3305),
	YANILLE("Yanille", 2577, 3090),
	PORT_KHAZARD("Port Khazard", 2653, 3159),
	BARBARIAN_VILLAGE("Barbarian Village", 3080, 3427),

	// Asgarnia and Misthalin.
	TAVERLEY("Taverley", 2910, 3451),
	BURTHORPE("Burthorpe", 2870, 3544),
	FALADOR("Falador", 3000, 3360),
	RIMMINGTON("Rimmington", 2956, 3232),
	PORT_SARIM("Port Sarim", 3029, 3221),
	ENTRANA("Entrana", 2840, 3358),
	EDGEVILLE("Edgeville", 3080, 3492),
	VARROCK("Varrock", 3210, 3448),
	LUMBER_YARD("Lumber Yard", 3298, 3518),
	LUMBRIDGE("Lumbridge", 3188, 3220),
	DRAYNOR_VILLAGE("Draynor Village", 3103, 3265),

	// Gnome and Feldip.
	TREE_GNOME_STRONGHOLD("Tree Gnome Stronghold", 2440, 3460),
	TREE_GNOME_VILLAGE("Tree Gnome Village", 2455, 3301),
	OBSERVATORY("Observatory", 2441, 3162),
	CASTLE_WARS("Castle Wars", 2407, 3105),
	JIGGIG("Jiggig", 2465, 3046),
	GU_TANOTH("Gu'Tanoth", 2524, 3035),
	FELDIP_HILLS("Feldip Hills", 2515, 2954),
	CORSAIR_COVE("Corsair Cove", 2573, 2856),
	MYTHS_GUILD("Myths' Guild", 2457, 2846),
	APE_ATOLL("Ape Atoll", 2750, 2750),

	// Karamja.
	BRIMHAVEN("Brimhaven", 2760, 3195),
	MUSA_POINT("Musa Point", 2904, 3162),
	TAI_BWO_WANNAI("Tai Bwo Wannai", 2795, 3065),

	// Fremennik and the north.
	RELLEKKA("Rellekka", 2658, 3677),
	MISCELLANIA("Miscellania", 2560, 3870),
	ETCETERIA("Etceteria", 2604, 3873),
	JATIZSO("Jatizso", 2405, 3805),
	NEITIZNOT("Neitiznot", 2331, 3802),
	LUNAR_ISLE("Lunar Isle", 2117, 3903),
	WEISS("Weiss", 2870, 3940),
	KELDAGRIM("Keldagrim", 2879, 10176),
	PISCATORIS("Piscatoris", 2333, 3576),
	MYNYDD("Mynydd", 2158, 3423),

	// Morytania.
	CANIFIS("Canifis", 3493, 3488),
	MORT_MYRE_SWAMP("Mort Myre Swamp", 3478, 3390),
	BURGH_DE_ROTT("Burgh de Rott", 3502, 3220),
	DARKMEYER("Darkmeyer", 3597, 3360),
	FOSSIL_ISLAND("Fossil Island", 3730, 3807),

	// Tirannwn.
	LLETYA("Lletya", 2338, 3171),
	ISAFDAR("Isafdar", 2244, 3182),
	PRIFDDINAS("Prifddinas", 3263, 6083),
	ZANARIS("Zanaris", 2430, 4428),
	ISLE_OF_SOULS("Isle of Souls", 2210, 2900),

	// Great Kourend.
	WOODCUTTING_GUILD("Woodcutting Guild", 1565, 3499),
	KOUREND_WOODLAND("Kourend Woodland", 1540, 3464),
	HOSIDIUS("Hosidius", 1762, 3598),
	ARCEUUS("Arceuus", 1700, 3800),
	LOVAKENGJ("Lovakengj", 1505, 3801),
	SHAYZIEN("Shayzien", 1517, 3592),
	PORT_PISCARILIUS("Port Piscarilius", 1803, 3752),
	FARMING_GUILD("Farming Guild", 1249, 3737),
	MOUNT_KARUULM("Mount Karuulm", 1311, 3807),

	// Varlamore.
	NEMUS_RETREAT("Nemus Retreat", 1376, 3310),
	QUETZACALLI_GORGE("Quetzacalli Gorge", 1505, 3232),
	THE_TEOMAT("The Teomat", 1449, 3185),
	RALOS_RISE("Ralos' Rise", 1450, 3140),
	CIVITAS_ILLA_FORTIS("Civitas illa Fortis", 1725, 3128),
	TAL_TEKLAN("Tal Teklan", 1223, 3111),
	OUTER_FORTIS("Outer Fortis", 1704, 3065),
	KASTORI("Kastori", 1373, 3042),
	AVIUM_SAVANNAH("Avium Savannah", 1635, 3010),
	SUNSET_COAST("Sunset Coast", 1530, 2983),
	STONECUTTER_OUTPOST("Stonecutter Outpost", 1740, 2963),
	ALDARIN("Aldarin", 1391, 2935);

	/** Default match radius, one region square. */
	private static final int DEFAULT_RADIUS_TILES = 64;

	private final String displayName;
	private final int centreX;
	private final int centreY;
	private final int radiusTiles;

	CallLocation(String displayName, int centreX, int centreY, int radiusTiles)
	{
		this.displayName = displayName;
		this.centreX = centreX;
		this.centreY = centreY;
		this.radiusTiles = radiusTiles;
	}

	CallLocation(String displayName, int centreX, int centreY)
	{
		this(displayName, centreX, centreY, DEFAULT_RADIUS_TILES);
	}

	/**
	 * Closest place to a coordinate, or null if nothing is near enough.
	 * <p>
	 * Nearest-wins rather than first-match, so places whose radii overlap still resolve to the one
	 * actually closest -- Drumstick Isle rather than Mynydd, a hundred tiles to its south.
	 */
	public static CallLocation nearest(int x, int y)
	{
		CallLocation best = null;
		int bestDistance = Integer.MAX_VALUE;

		for (CallLocation location : values())
		{
			int distance = location.tilesFrom(x, y);
			if (distance <= location.radiusTiles && distance < bestDistance)
			{
				best = location;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Chebyshev distance, as the game measures it. */
	int tilesFrom(int x, int y)
	{
		return Math.max(Math.abs(x - centreX), Math.abs(y - centreY));
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
