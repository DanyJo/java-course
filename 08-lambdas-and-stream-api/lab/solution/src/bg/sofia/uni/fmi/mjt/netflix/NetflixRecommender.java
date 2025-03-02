package bg.sofia.uni.fmi.mjt.netflix;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NetflixRecommender {

    private static final int SENSITIVITY_THRESHOLD = 10_000;
    private static final String SPLIT_TO_WORDS_REGEX = "[\\p{IsPunctuation}\\s]+";

    private final List<Content> contents;

    /**
     * Loads the dataset from the given {@code reader}.
     *
     * @param reader Reader from which the dataset can be read.
     */
    public NetflixRecommender(Reader reader) {
        try (var br = new BufferedReader(reader)) {

            contents = br.lines().skip(1).map(Content::of).toList();

            System.out.println("Movies loaded: " + contents.size());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not load dataset", ex);
        }
    }

    /**
     * Returns all movies and shows from the dataset in undefined order as an unmodifiable List.
     * If the dataset is empty, returns an empty List.
     *
     * @return the list of all movies and shows.
     */
    public List<Content> getAllContent() {
        return List.copyOf(contents);
    }

    /**
     * Returns a list of all unique genres of movies and shows in the dataset in undefined order.
     * If the dataset is empty, returns an empty List.
     *
     * @return the list of all genres
     */
    public List<String> getAllGenres() {
        return contents.stream()
            .flatMap(s -> s.genres().stream())
            .distinct()
            .toList();
    }

    /**
     * Returns the movie with the longest duration / run time. If there are two or more movies
     * with equal maximum run time, returns any of them. Shows in the dataset are not considered by this method.
     *
     * @return the movie with the longest run time
     * @throws NoSuchElementException in case there are no movies in the dataset.
     */
    public Content getTheLongestMovie() {
        return contents.stream()
            .filter(content -> ContentType.MOVIE == content.type())
            .max(Comparator.comparingInt(Content::runtime))
            .orElseThrow(NoSuchElementException::new);
    }

    /**
     * Returns a breakdown of content by type (movie or show).
     *
     * @return a Map with key: a ContentType and value: the set of movies or shows on the dataset, in undefined order.
     */
    public Map<ContentType, Set<Content>> groupContentByType() {
        return contents.stream()
            .collect(Collectors.groupingBy(Content::type, Collectors.toSet()));
    }

    /**
     * Returns the top N movies and shows sorted by weighed IMDB rating in descending order.
     * If there are fewer movies and shows than {@code n} in the dataset, return all of them.
     * If {@code n} is zero, returns an empty list.
     * <p>
     * The weighed rating is calculated by the following formula:
     * Weighted Rating (WR) = (v ÷ (v + m)) × R + (m ÷ (v + m)) × C
     * where
     * R is the content's own average rating across all votes. If it has no votes, its R is 0.
     * C is the average rating of content across the dataset
     * v is the number of votes for a content
     * m is a tunable parameter: sensitivity threshold. In our algorithm, it's a constant equal to 10_000.
     * <p>
     * Check https://stackoverflow.com/questions/1411199/what-is-a-better-way-to-sort-by-a-5-star-rating for details.
     *
     * @param n the number of the top-rated movies and shows to return
     * @return the list of the top-rated movies and shows
     * @throws IllegalArgumentException if {@code n} is negative.
     */
    public List<Content> getTopNRatedContent(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("N should be a non-negative integer");
        }

        double averageItemsScore = contents.stream()
            .collect(Collectors.averagingDouble(Content::imdbScore));

        return contents.stream()
            .sorted((m1, m2) -> Double.compare(
                calculateWeightedRating(m2, averageItemsScore),
                calculateWeightedRating(m1, averageItemsScore))
            )
            .limit(n)
            .toList();
    }

    /**
     * Returns a list of content similar to the specified one sorted by similarity is descending order.
     * Two contents are considered similar, only if they are of the same type (movie or show).
     * The used measure of similarity is the number of genres two contents share.
     * If two contents have equal number of common genres with the specified one, their mutual oder
     * in the result is undefined.
     *
     * @param content the specified movie or show.
     * @return the sorted list of content similar to the specified one.
     */
    public List<Content> getSimilarContent(Content content) {
        return contents.stream()
            .filter(otherContent -> otherContent.type() == content.type())
            .sorted((c1, c2) -> {
                Set<String> genres1 = new HashSet<>(c1.genres());
                genres1.retainAll(content.genres());

                Set<String> genres2 = new HashSet<>(c2.genres());
                genres2.retainAll(content.genres());
                return Integer.compare(genres2.size(), genres1.size());

            }).toList();
    }

    /**
     * Searches content by keywords in the description (case-insensitive).
     *
     * @param keywords the keywords to search for
     * @return an unmodifiable set of movies and shows whose description contains all specified keywords.
     */
    public Set<Content> getContentByKeywords(String... keywords) {
        Set<String> normalizedKeywords = Arrays.stream(keywords)
            .map(String::toLowerCase)
            .map(String::strip)
            .collect(Collectors.toSet());

        return contents.stream()
            .filter(c -> {
                Set<String> words = Pattern.compile(SPLIT_TO_WORDS_REGEX)
                    .splitAsStream(c.description())
                    .map(String::toLowerCase)
                    .map(String::strip)
                    .collect(Collectors.toSet());
                return words.containsAll(normalizedKeywords);
            })
            .collect(Collectors.toUnmodifiableSet());
    }

    private double calculateWeightedRating(Content content, double averageItemsScore) {
        return (content.imdbVotes() / (content.imdbVotes() + SENSITIVITY_THRESHOLD)) * content.imdbScore() +
            (SENSITIVITY_THRESHOLD / (content.imdbVotes() + SENSITIVITY_THRESHOLD)) * averageItemsScore;
    }

}
