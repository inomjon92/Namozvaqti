package uz.namozvaqti.app;

/** O'zbekiston hududlari va shaharlari (koordinatalar ilova ichida, internetsiz). */
public final class Cities {

    public static final class City {
        public final String name;
        public final double lat, lng;

        City(String name, double lat, double lng) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }
    }

    public static final class Region {
        public final String name;
        public final City[] cities;

        Region(String name, City... cities) {
            this.name = name;
            this.cities = cities;
        }
    }

    public static final Region[] REGIONS = {
            new Region("Toshkent shahri", new City("Toshkent", 41.2995, 69.2401)),
            new Region("Toshkent viloyati",
                    new City("Nurafshon", 41.0167, 69.3417),
                    new City("Chirchiq", 41.4689, 69.5822),
                    new City("Angren", 41.0167, 70.1436),
                    new City("Olmaliq", 40.8447, 69.5983),
                    new City("Bekobod", 40.2208, 69.2697),
                    new City("Yangiyo'l", 41.1122, 69.0472)),
            new Region("Andijon viloyati",
                    new City("Andijon", 40.7821, 72.3442),
                    new City("Asaka", 40.6415, 72.2389),
                    new City("Xonobod", 40.8000, 73.0000),
                    new City("Shahrixon", 40.7139, 72.0569)),
            new Region("Farg'ona viloyati",
                    new City("Farg'ona", 40.3842, 71.7843),
                    new City("Qo'qon", 40.5286, 70.9425),
                    new City("Marg'ilon", 40.4711, 71.7247),
                    new City("Quvasoy", 40.2972, 71.9797),
                    new City("Rishton", 40.3567, 71.2847)),
            new Region("Namangan viloyati",
                    new City("Namangan", 40.9983, 71.6726),
                    new City("Chust", 41.0033, 71.2372),
                    new City("Pop", 40.8736, 71.1089),
                    new City("Kosonsoy", 41.2494, 71.5500)),
            new Region("Sirdaryo viloyati",
                    new City("Guliston", 40.4897, 68.7842),
                    new City("Yangiyer", 40.2750, 68.8225),
                    new City("Shirin", 40.2269, 69.1172)),
            new Region("Jizzax viloyati",
                    new City("Jizzax", 40.1158, 67.8422),
                    new City("G'allaorol", 40.0294, 67.5953),
                    new City("Do'stlik", 40.5247, 68.0358)),
            new Region("Samarqand viloyati",
                    new City("Samarqand", 39.6542, 66.9597),
                    new City("Kattaqo'rg'on", 39.8989, 66.2561),
                    new City("Urgut", 39.4022, 67.2431),
                    new City("Ishtixon", 39.9667, 66.4861)),
            new Region("Buxoro viloyati",
                    new City("Buxoro", 39.7747, 64.4286),
                    new City("G'ijduvon", 40.1000, 64.6833),
                    new City("Kogon", 39.7228, 64.5517),
                    new City("Qorako'l", 39.5050, 63.8536)),
            new Region("Navoiy viloyati",
                    new City("Navoiy", 40.0844, 65.3792),
                    new City("Zarafshon", 41.5747, 64.2014),
                    new City("Uchquduq", 42.1567, 63.5522),
                    new City("Nurota", 40.5614, 65.6886)),
            new Region("Qashqadaryo viloyati",
                    new City("Qarshi", 38.8606, 65.7891),
                    new City("Shahrisabz", 39.0578, 66.8342),
                    new City("Kitob", 39.1200, 66.8800),
                    new City("G'uzor", 38.6208, 66.2481)),
            new Region("Surxondaryo viloyati",
                    new City("Termiz", 37.2242, 67.2783),
                    new City("Denov", 38.2667, 67.9000),
                    new City("Sherobod", 37.6667, 67.0000),
                    new City("Boysun", 38.2061, 67.2006)),
            new Region("Xorazm viloyati",
                    new City("Urganch", 41.5500, 60.6333),
                    new City("Xiva", 41.3783, 60.3639),
                    new City("Xonqa", 41.4747, 60.7750)),
            new Region("Qoraqalpog'iston Respublikasi",
                    new City("Nukus", 42.4600, 59.6100),
                    new City("Xo'jayli", 42.4047, 59.4517),
                    new City("To'rtko'l", 41.5500, 61.0000),
                    new City("Mo'ynoq", 43.7683, 59.0214)),
    };

    /** Eng yaqin shaharni topadi: {region, shahar} indekslari. */
    public static int[] nearest(double lat, double lng) {
        int[] best = {0, 0};
        double bestD = Double.MAX_VALUE;
        for (int r = 0; r < REGIONS.length; r++) {
            for (int c = 0; c < REGIONS[r].cities.length; c++) {
                City city = REGIONS[r].cities[c];
                double dLat = city.lat - lat;
                double dLng = (city.lng - lng) * Math.cos(Math.toRadians(lat));
                double d = dLat * dLat + dLng * dLng;
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{r, c};
                }
            }
        }
        return best;
    }
}
