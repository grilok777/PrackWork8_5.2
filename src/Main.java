import java.util.concurrent.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static final Map<String, CompletableFuture<Map<String, Double>>> weatherCache = new ConcurrentHashMap<>();

    // Метод для отримання погоди в місті (імітуємо затримку)
    private static CompletableFuture<Map<String, Double>> getWeather(String city) {
        return weatherCache.computeIfAbsent(city, c -> CompletableFuture.supplyAsync(() -> {
            System.out.println("Отримання погоди для " + city);
            try {
                Thread.sleep(1000 + (int) (Math.random() * 2000)); // Імітація затримки
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Імітація даних погоди
            Map<String, Double> weatherData = new HashMap<>();
            weatherData.put("температура", 15 + Math.random() * 20); // Температура
            weatherData.put("вологість", 40 + Math.random() * 30); // Вологість
            weatherData.put("швидкість_вітру", 5 + Math.random() * 10); // Швидкість вітру
            weatherData.put("тиск", 1000 + Math.random() * 20); // Додали тиск
            System.out.println("Погода в " + city + ": " + formatWeatherData(weatherData));
            return weatherData;
        }));
    }

    // Метод для форматування погоди
    private static String formatWeatherData(Map<String, Double> weatherData) {
        return String.format("{температура: %.2f, вологість: %.2f, швидкість_вітру: %.2f, тиск: %.2f}",
                weatherData.get("температура"), weatherData.get("вологість"), weatherData.get("швидкість_вітру"), weatherData.get("тиск"));
    }

    // Метод для отримання рекомендації на основі погоди (для thenCompose)
    private static CompletableFuture<String> getRecommendation(String city, Map<String, Double> weatherData) {
        return CompletableFuture.supplyAsync(() -> {
            double temp = weatherData.get("температура");
            String recommendation;
            if (temp > 25) {
                recommendation = "Чудова погода для пляжу!";
            } else if (temp < 10) {
                recommendation = "Зараз досить прохолодно.";
            } else {
                recommendation = "Гарна погода для прогулянки!";
            }
            return "Рекомендація для " + city + ": " + recommendation;
        });
    }

    // Метод для комбінування погоди та рекомендації (для thenCombine)
    private static CompletableFuture<String> combineWeatherAndRecommendation(String city, Map<String, Double> weatherData, String recommendation) {
        return CompletableFuture.supplyAsync(() -> {
            return "Погода в " + city + ": " + formatWeatherData(weatherData) + ", " + recommendation;
        });
    }

    public static void main(String[] args) {
        List<String> cities = Arrays.asList("Київ", "Одеса", "Львів", "Харків", "Дніпро");

        // 1. Демонстрація thenCompose
        CompletableFuture<List<String>> recommendations = CompletableFuture.supplyAsync(() -> cities)
                .thenCompose(cityList -> {
                    List<CompletableFuture<String>> futureRecommendations = cityList.stream()
                            .map(city -> getWeather(city)
                                    .thenCompose(weatherData -> getRecommendation(city, weatherData)))
                            .collect(Collectors.toList());
                    return CompletableFuture.allOf(futureRecommendations.toArray(new CompletableFuture[0]))
                            .thenApply(v -> futureRecommendations.stream()
                                    .map(CompletableFuture::join)
                                    .collect(Collectors.toList()));
                });
        recommendations.thenAccept(recs -> {
            System.out.println("--- Рекомендації по містах (thenCompose) ---");
            for (String rec : recs) {
                System.out.println(rec);
            }
        });

        // 2. Демонстрація thenCombine
        CompletableFuture<List<String>> weatherAndRecommendation = CompletableFuture.supplyAsync(() -> cities)
                .thenCompose(cityList -> {
                    List<CompletableFuture<String>> combinedFutures = cityList.stream()
                            .map(city -> getWeather(city)
                                    .thenCombine(getRecommendation(city, weatherCache.get(city).join()), (weather, rec) ->
                                            "Погода в " + city + ": " + formatWeatherData(weather) + ", " + rec))
                            .collect(Collectors.toList());
                    return CompletableFuture.allOf(combinedFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> combinedFutures.stream()
                                    .map(CompletableFuture::join)
                                    .collect(Collectors.toList()));
                });
        weatherAndRecommendation.thenAccept(combinedResults -> {
            System.out.println("--- Погода і рекомендації по містах (thenCombine) ---");
            for (String result : combinedResults) {
                System.out.println(result);
            }
        });


        // 3. Пошук найкращого міста для пляжу, найхолоднішого та з найбільшим тиском
        CompletableFuture<Void> analysisFuture = CompletableFuture.supplyAsync(() -> cities)
                .thenCompose(cityList -> {
                    List<CompletableFuture<Map<String, Double>>> weatherFutures = cityList.stream()
                            .map(Main::getWeather)
                            .collect(Collectors.toList());
                    return CompletableFuture.allOf(weatherFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> weatherFutures.stream()
                                    .map(CompletableFuture::join)
                                    .collect(Collectors.toList()));
                })
                .thenAccept(weatherDataList -> {
                    String bestBeachCity = null;
                    double maxTemp = Double.MIN_VALUE;

                    String coldestCity = null;
                    double minTemp = Double.MAX_VALUE;

                    String highestPressureCity = null;
                    double maxPressure = Double.MIN_VALUE;

                    for (int i = 0; i < cities.size(); i++) {
                        Map<String, Double> weather = weatherDataList.get(i);
                        String city = cities.get(i);

                        double temp = weather.get("температура");
                        double pressure = weather.get("тиск");

                        // Для пляжу (макс. температура)
                        if (temp > maxTemp) {
                            maxTemp = temp;
                            bestBeachCity = city;
                        }

                        // Для найхолоднішого (мін. температура)
                        if (temp < minTemp) {
                            minTemp = temp;
                            coldestCity = city;
                        }

                        // Для найбільшого тиску
                        if (pressure > maxPressure) {
                            maxPressure = pressure;
                            highestPressureCity = city;
                        }
                    }
                    System.out.println("--- Аналіз погоди ---");
                    if (bestBeachCity != null) {
                        System.out.println("Найкраще місто для пляжу: " + bestBeachCity + " (температура: " + maxTemp + ")");
                    } else {
                        System.out.println("Інформація про найкраще місто для пляжу відсутня");
                    }
                    if (coldestCity != null) {
                        System.out.println("Найхолодніше місто: " + coldestCity + " (температура: " + minTemp + ")");
                    } else {
                        System.out.println("Інформація про найхолодніше місто відсутня");
                    }
                    if (highestPressureCity != null) {
                        System.out.println("Місто з найбільшим тиском: " + highestPressureCity + " (тиск: " + maxPressure + ")");
                    } else {
                        System.out.println("Інформація про місто з найбільшим тиском відсутня");
                    }
                });

        // Чекаємо завершення всіх завдань (для демонстрації в консольному додатку)
        try {
            CompletableFuture.allOf(recommendations, weatherAndRecommendation, analysisFuture).get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}