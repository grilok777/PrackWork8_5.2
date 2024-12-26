import java.util.concurrent.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    // Метод для отримання погоди в місті (імітуємо затримку)
    private static CompletableFuture<Map<String, Double>> getWeather(String city) {
        return CompletableFuture.supplyAsync(() -> {
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
        });
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
    private static CompletableFuture<String> combineWeatherAndRecommendation(String city,Map<String, Double> weatherData, String recommendation) {
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


        // Отримуємо погоду для всіх міст паралельно
        List<CompletableFuture<Map<String, Double>>> weatherFutures = cities.stream()
                .map(Main::getWeather)
                .collect(Collectors.toList());

        // 2. Демонстрація thenCombine
        CompletableFuture<List<String>> weatherAndRecommendation = CompletableFuture.supplyAsync(() -> cities)
                .thenCompose(cityList -> {
                    List<CompletableFuture<String>> combinedFutures = cityList.stream()
                            .map(city -> getWeather(city)
                                    .thenCombine(getRecommendation(city,new HashMap<>()), (weather, rec) ->
                                            combineWeatherAndRecommendation(city,weather,rec)))
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList());
                    return CompletableFuture.allOf(combinedFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> combinedFutures.stream()
                                    .map(CompletableFuture::join)
                                    .collect(Collectors.toList()));
                });
        weatherAndRecommendation.thenAccept(combinedResults -> {
            System.out.println("--- Погода і рекомендації по містах (thenCombine) ---");
            for (String result : combinedResults){
                System.out.println(result);
            }
        });


        // Об'єднуємо всі CompletableFuture в один
        CompletableFuture<Void> allCities = CompletableFuture.allOf(weatherFutures.toArray(new CompletableFuture[0]));

        // Обробка результатів після отримання даних для всіх міст
        allCities.thenRun(() -> {
            try {
                List<Map<String, Double>> allWeatherData = weatherFutures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList());

                System.out.println("--- Фінальні дані про погоду ---");

                for (int i = 0; i < cities.size(); i++) {
                    System.out.println(cities.get(i) + ": " + formatWeatherData(allWeatherData.get(i)));
                }

                //Знаходимо місто з найкращою погодою для пляжу
                String bestCityForBeach = allWeatherData.stream()
                        .map(data -> {
                            int cityIndex = allWeatherData.indexOf(data);
                            return (data.get("температура") > 25 && data.get("швидкість_вітру") < 10) ? cities.get(cityIndex) : null;
                        })
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse("Краще піти в кафе");
                System.out.println("Найкраще місто, щоб піти на пляж: " + bestCityForBeach);

                //Знаходимо найхолодніше місто
                String coldestCity = cities.get(0);
                double coldestTemp = allWeatherData.get(0).get("температура");
                for (int i = 1; i < allWeatherData.size(); i++) {
                    double currentTemp = allWeatherData.get(i).get("температура");
                    if (currentTemp < coldestTemp) {
                        coldestTemp = currentTemp;
                        coldestCity = cities.get(i);
                    }
                }
                System.out.println("Найхолодніше місто: " + coldestCity);

                // Знаходимо місто з найбільшим тиском
                String highestPressureCity = cities.get(0);
                double highestPressure = allWeatherData.get(0).get("тиск");
                for (int i = 1; i < allWeatherData.size(); i++) {
                    double currentPressure = allWeatherData.get(i).get("тиск");
                    if (currentPressure > highestPressure) {
                        highestPressure = currentPressure;
                        highestPressureCity = cities.get(i);
                    }
                }
                System.out.println("Місто з найвищим тиском: " + highestPressureCity);

                //Виводимо середню температуру по всім містам
                double averageTemperature = allWeatherData.stream()
                        .mapToDouble(data -> data.get("температура"))
                        .average()
                        .orElse(Double.NaN);
                System.out.println("Середня температура по всіх містах: " + String.format("%.2f", averageTemperature));

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Завершення при готовності першого міста (anyOf)
        CompletableFuture<Object> firstCompleted = CompletableFuture.anyOf(weatherFutures.toArray(new CompletableFuture[0]));
        firstCompleted.thenAccept(result -> {
            System.out.println("Перші заповнені дані про погоду: " + formatWeatherData((Map<String, Double>) result));
        });

        // Чекаємо завершення всіх завдань (для демонстрації в консольному додатку)
        try {
            allCities.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}