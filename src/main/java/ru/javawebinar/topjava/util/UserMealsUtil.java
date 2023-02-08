package ru.javawebinar.topjava.util;

import ru.javawebinar.topjava.model.UserMeal;
import ru.javawebinar.topjava.model.UserMealWithExcess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;

public class UserMealsUtil {
    public static void main(String[] args) {
        List<UserMeal> meals = Arrays.asList(
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 30, 10, 0), "Завтрак", 500),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 30, 13, 0), "Обед", 1000),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 30, 20, 0), "Ужин", 500),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 0, 0), "Еда на граничное значение", 100),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 10, 0), "Завтрак", 1000),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 13, 0), "Обед", 500),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 20, 0), "Ужин", 410)
        );

        List<UserMealWithExcess> mealsTo = filteredByCycles(meals, LocalTime.of(7, 0), LocalTime.of(12, 0), 2000);
        mealsTo.forEach(System.out::println);

        System.out.println(filteredByStreams(meals, LocalTime.of(7, 0), LocalTime.of(12, 0), 2000));

        System.out.println(filteredByStreamsCustomCollector(meals, LocalTime.of(7, 0), LocalTime.of(12, 0), 2000));


    }

    public static List<UserMealWithExcess> filteredByCycles(List<UserMeal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {

        Map<LocalDate, Integer> dateToCalriesMap = new HashMap<>();
        List<UserMeal> mealsFilteredByTime = new ArrayList<>();
        for (UserMeal meal : meals) {
            LocalDate date = meal.getDateTime().toLocalDate();
            Integer calories = meal.getCalories();
            if (dateToCalriesMap.containsKey(date)) {
                dateToCalriesMap.put(date, dateToCalriesMap.get(date) + calories);
            } else {
                dateToCalriesMap.put(date, calories);
            }
            if (TimeUtil.isBetweenHalfOpen(meal.getDateTime().toLocalTime(), startTime, endTime))
                mealsFilteredByTime.add(meal);
        }

        List<UserMealWithExcess> result = new ArrayList<>();
        for (UserMeal meal : mealsFilteredByTime) {
            boolean excess = dateToCalriesMap.get(meal.getDateTime().toLocalDate()) > caloriesPerDay;
            result.add(new UserMealWithExcess(meal.getDateTime(), meal.getDescription(), meal.getCalories(), excess));
        }

        return result;
    }

    public static List<UserMealWithExcess> filteredByStreams(List<UserMeal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {

        Map<LocalDate, Integer> caloriesPerDayMap = meals.stream()
                .collect(groupingBy(meal -> meal.getDateTime().toLocalDate(), summingInt(UserMeal::getCalories)));

        List<UserMealWithExcess> result = meals.stream()
                .filter(meal -> TimeUtil.isBetweenHalfOpen(meal.getDateTime().toLocalTime(), startTime, endTime))
                .map(meal -> new UserMealWithExcess(meal.getDateTime(), meal.getDescription(), meal.getCalories(),
                        caloriesPerDayMap.get(meal.getDateTime().toLocalDate()) > caloriesPerDay))
                .collect(Collectors.toList());
        return result;
    }

    public static List<UserMealWithExcess> filteredByStreamsCustomCollector(List<UserMeal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {

        class Accumulator implements Supplier<Accumulator> {
            final Map<LocalDate, Integer> caloriesMap;
            final List<UserMeal> meals;

            Accumulator() {
                caloriesMap = new HashMap<>();
                meals = new ArrayList<>();
            }

            @Override
            public Accumulator get() {
                return this;
            }
        }

        class MealsCollector implements Collector<UserMeal, Accumulator, List<UserMealWithExcess>> {

            @Override
            public Supplier<Accumulator> supplier() {
                return new Accumulator();
            }

            @Override
            public BiConsumer<Accumulator, UserMeal> accumulator() {

                return (Accumulator acc, UserMeal meal) -> {
                    LocalDate date = meal.getDateTime().toLocalDate();
                    Integer calories = meal.getCalories();
                    if (acc.caloriesMap.containsKey(date)) {
                        acc.caloriesMap.put(date, acc.caloriesMap.get(date) + calories);
                    } else {
                        acc.caloriesMap.put(date, calories);
                    }
                    if (TimeUtil.isBetweenHalfOpen(meal.getDateTime().toLocalTime(), startTime, endTime)) {
                        acc.meals.add(meal);
                    }
                };
            }

            @Override
            public BinaryOperator<Accumulator> combiner() {
                return (Accumulator acc1, Accumulator acc2) -> {
                    acc2.caloriesMap.forEach((key, value) -> acc1.caloriesMap.merge(key, value, Integer::sum));
                    acc1.meals.addAll(acc2.meals);
                    return acc1;
                };
            }

            @Override
            public Function<Accumulator, List<UserMealWithExcess>> finisher() {
                return (Accumulator acc) -> acc.meals.stream()
                        .map(meal -> new UserMealWithExcess(meal.getDateTime(),
                                meal.getDescription(),
                                meal.getCalories(),
                                acc.caloriesMap.get(meal.getDateTime().toLocalDate()) > caloriesPerDay))
                        .collect(Collectors.toList());
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.unmodifiableSet(EnumSet.of(Characteristics.UNORDERED)); }
        }

        return meals.stream().collect(new MealsCollector());

    }


}
