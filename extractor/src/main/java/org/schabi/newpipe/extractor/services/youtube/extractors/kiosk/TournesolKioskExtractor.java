package org.schabi.newpipe.extractor.services.youtube.extractors.kiosk;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.Utils;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TournesolKioskExtractor extends KioskExtractor<StreamInfoItem> {

    public static final String KIOSK_ID = "Tournesol";
    private static final String BASE_API_URL =
            "https://api.tournesol.app/polls/videos/recommendations/";

    private JsonObject initialData;
    private List<String> languages;
    private String dateGte;
    private String uploader;
    private boolean includeUnsafe;

    public TournesolKioskExtractor(final StreamingService service,
                                   final ListLinkHandler linkHandler,
                                   final String kioskId) {
        super(service, linkHandler, kioskId);
        // Default behavior: 30 days ago, filtered by fr, en, es
        this.languages = Arrays.asList("fr", "en", "es");
        this.dateGte = calculateDaysAgo(30);
        this.uploader = null;
        this.includeUnsafe = false;
    }

    private String calculateDaysAgo(int days) {
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -days);
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(cal.getTime());
    }

    public void setLanguages(final List<String> languages) {
        this.languages = languages;
    }

    public void setDateGte(final String dateGte) {
        this.dateGte = dateGte;
    }

    public void setUploader(final String uploader) {
        this.uploader = uploader;
    }

    public void setIncludeUnsafe(final boolean includeUnsafe) {
        this.includeUnsafe = includeUnsafe;
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {

        final StringBuilder urlBuilder = new StringBuilder(BASE_API_URL);
        urlBuilder.append("?limit=20");

        if (dateGte != null && !dateGte.isEmpty()) {
            try {
                urlBuilder.append("&date_gte=").append(URLEncoder.encode(dateGte, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                // Should not happen
                urlBuilder.append("&date_gte=").append(dateGte);
            }
        }

        if (languages != null && !languages.isEmpty()) {
            for (String lang : languages) {
                try {
                    urlBuilder.append("&metadata[language]=").append(URLEncoder.encode(lang, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    urlBuilder.append("&metadata[language]=").append(lang);
                }
            }
        }

        if (!Utils.isNullOrEmpty(uploader)) {
            try {
                urlBuilder.append("&metadata[uploader]=")
                        .append(URLEncoder.encode(uploader, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                urlBuilder.append("&metadata[uploader]=").append(uploader);
            }
        }
        if (includeUnsafe) {
            urlBuilder.append("&unsafe=true");
        }

        final String apiUrl = urlBuilder.toString();

        final String response = downloader.get(apiUrl, Collections.singletonMap("Accept",
                Collections.singletonList("application/json"))).responseBody();

        initialData = parseResponse(response);
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return "Tournesol Recommendations";
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws ParsingException {
        if (initialData == null) {
            throw new ParsingException("Tournesol API response is missing");
        }
        return parseInfoItemsPage(initialData);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || Utils.isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }
        final String pageUrl = includeUnsafe ? ensureUnsafeParam(page.getUrl()) : page.getUrl();
        final String response = getDownloader().get(pageUrl, Collections.singletonMap("Accept",
                Collections.singletonList("application/json"))).responseBody();
        final JsonObject pageData = parseResponse(response);
        return parseInfoItemsPage(pageData);
    }

    @Nonnull
    private static String ensureUnsafeParam(@Nonnull final String url) {
        if (url.contains("unsafe=")) {
            return url;
        }
        final String separator = url.contains("?") ? "&" : "?";
        return url + separator + "unsafe=true";
    }

    private JsonObject parseResponse(final String response) throws ParsingException {
        if (Utils.isNullOrEmpty(response)) {
            throw new ParsingException("Empty Tournesol API response");
        }
        try {
            return JsonParser.object().from(response);
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse Tournesol API response", e);
        }
    }

    private InfoItemsPage<StreamInfoItem> parseInfoItemsPage(final JsonObject data)
            throws ParsingException {
        final StreamInfoItemsCollector collector =
                new StreamInfoItemsCollector(getServiceId());

        if (data.has("results")) {
            final JsonArray results = data.getArray("results");

            for (final Object resultObj : results) {
                if (resultObj instanceof JsonObject) {
                    final JsonObject result = (JsonObject) resultObj;
                    final JsonObject entity = result.getObject("entity");

                    if (entity != null) {
                        final Long tournesolScore = extractTournesolScore(result);
                        final List<String> unsafeReasons = extractUnsafeReasons(result);
                        final String uid = entity.getString("uid");
                        final Object metadataObj = entity.get("metadata");
                        JsonObject metadata = null;
                        if (metadataObj instanceof JsonObject) {
                            metadata = (JsonObject) metadataObj;
                        }

                        String title = getString(entity, "name");
                        String uploader = null;
                        String uploaderUrl = null;
                        long duration = -1;
                        long viewCount = -1;
                        String url = null;
                        String videoId = null;
                        String thumbnail = null;
                        String textualUploadDate = null;
                        DateWrapper uploadDate = null;

                        if (metadata != null) {
                            title = firstNonEmpty(
                                    getString(metadata, "title"),
                                    getString(metadata, "name"),
                                    title);
                            uploader = getString(metadata, "uploader");
                            duration = getLong(metadata, "duration", -1);
                            viewCount = getLong(metadata, "views", -1);
                            videoId = getString(metadata, "video_id");
                            url = getString(metadata, "url");

                            final String channelId = getString(metadata, "channel_id");
                            if (!Utils.isNullOrEmpty(channelId)) {
                                uploaderUrl = "https://www.youtube.com/channel/" + channelId;
                            }

                            textualUploadDate = getString(metadata, "publication_date");
                            uploadDate = parseUploadDate(textualUploadDate);

                            thumbnail = firstNonEmpty(
                                    getString(metadata, "thumbnail"),
                                    getString(metadata, "thumbnail_url"),
                                    getString(metadata, "thumbnailUrl"));
                        }

                        if (Utils.isNullOrEmpty(videoId)) {
                            videoId = normalizeVideoId(uid);
                        }
                        if (Utils.isNullOrEmpty(url) && !Utils.isNullOrEmpty(videoId)) {
                            url = "https://www.youtube.com/watch?v=" + videoId;
                        }
                        if (Utils.isNullOrEmpty(thumbnail) && !Utils.isNullOrEmpty(videoId)) {
                            thumbnail = "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
                        }

                        if (!Utils.isNullOrEmpty(url) && !Utils.isNullOrEmpty(title)) {
                            final String finalUrl = url;
                            final String finalUploader = uploader;
                            final String finalUploaderUrl = uploaderUrl;
                            final long finalDuration = duration;
                            final long finalViewCount = viewCount;
                            final String finalTitle = title;
                            final String finalThumbnail = thumbnail;
                            final String finalTextualUploadDate = textualUploadDate;
                            final DateWrapper finalUploadDate = uploadDate;
                            final List<String> finalUnsafeReasons = unsafeReasons;

                            collector.commit(new StreamInfoItemExtractor() {
                                @Override
                                public StreamType getStreamType() throws ParsingException {
                                    return StreamType.VIDEO_STREAM;
                                }

                                @Override
                                public boolean isAd() throws ParsingException {
                                    return false;
                                }

                                @Override
                                public long getDuration() throws ParsingException {
                                    return finalDuration;
                                }

                                @Override
                                public long getViewCount() throws ParsingException {
                                    return finalViewCount;
                                }

                                @Override
                                public String getUploaderName() throws ParsingException {
                                    return finalUploader;
                                }

                                @Override
                                public String getUploaderUrl() throws ParsingException {
                                    return finalUploaderUrl;
                                }

                                @Override
                                public boolean isUploaderVerified() throws ParsingException {
                                    return false;
                                }

                                @Nullable
                                @Override
                                public String getTextualUploadDate() throws ParsingException {
                                    return finalTextualUploadDate;
                                }

                                @Nullable
                                @Override
                                public DateWrapper getUploadDate() throws ParsingException {
                                    return finalUploadDate;
                                }

                                @Nonnull
                                @Override
                                public String getName() throws ParsingException {
                                    return finalTitle;
                                }

                                @Nonnull
                                @Override
                                public String getUrl() throws ParsingException {
                                    return finalUrl;
                                }

                                @Nonnull
                                @Override
                                public List<Image> getThumbnails() throws ParsingException {
                                    if (Utils.isNullOrEmpty(finalThumbnail)) {
                                        return Collections.emptyList();
                                    }
                                    return Collections.singletonList(new Image(finalThumbnail,
                                            Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN,
                                            Image.ResolutionLevel.UNKNOWN));
                                }

                                @Nullable
                                @Override
                                public Long getTournesolScore() throws ParsingException {
                                    return tournesolScore;
                                }

                                @Nonnull
                                @Override
                                public List<String> getTournesolUnsafeReasons()
                                        throws ParsingException {
                                    return finalUnsafeReasons;
                                }
                            });
                        }
                    }
                }
            }
        }

        Page nextPage = null;
        if (data.has("next")) {
            final Object nextObj = data.get("next");
            if (nextObj instanceof String) {
                final String nextUrl = (String) nextObj;
                if (!Utils.isNullOrEmpty(nextUrl)) {
                    nextPage = new Page(nextUrl);
                }
            }
        }

        return new InfoItemsPage<>(collector, nextPage);
    }

    @Nonnull
    private static List<String> extractUnsafeReasons(@Nullable final JsonObject result) {
        if (result == null) {
            return Collections.emptyList();
        }
        final JsonObject collectiveRating = result.getObject("collective_rating");
        if (collectiveRating == null) {
            return Collections.emptyList();
        }

        final JsonObject unsafe = collectiveRating.getObject("unsafe");
        final Object unsafeStatus = unsafe == null ? null : unsafe.get("status");
        if (!(unsafeStatus instanceof Boolean) || !((Boolean) unsafeStatus)) {
            return Collections.emptyList();
        }

        final Object reasonsObj = unsafe.get("reasons");
        if (!(reasonsObj instanceof JsonArray)) {
            return Collections.emptyList();
        }

        final List<String> reasons = new ArrayList<>();
        for (final Object reasonObj : (JsonArray) reasonsObj) {
            if (reasonObj instanceof String && !Utils.isNullOrEmpty((String) reasonObj)) {
                reasons.add((String) reasonObj);
            }
        }

        return reasons;
    }

    @Nullable
    private static Long extractTournesolScore(final JsonObject result) {
        if (result == null) {
            return null;
        }
        Double score = extractScoreValue(result.get("score"));
        if (score == null && result.has("scores")) {
            score = extractScoreValue(result.get("scores"));
        }
        if (score == null && result.has("collective_rating")) {
            score = extractScoreValue(result.get("collective_rating"));
        }
        if (score == null && result.has("recommendation_metadata")) {
            score = extractScoreValue(result.get("recommendation_metadata"));
        }
        if (score == null && result.has("entity_contexts")) {
            score = extractScoreValue(result.get("entity_contexts"));
        }
        if (score == null) {
            return null;
        }
        return Math.round(score);
    }

    @Nullable
    private static Double extractScoreValue(@Nullable final Object scoreObj) {
        if (scoreObj instanceof Number) {
            return ((Number) scoreObj).doubleValue();
        }
        if (scoreObj instanceof String) {
            try {
                return Double.parseDouble((String) scoreObj);
            } catch (final NumberFormatException ignored) {
                return null;
            }
        }
        if (scoreObj instanceof JsonArray) {
            final JsonArray array = (JsonArray) scoreObj;
            for (final Object entry : array) {
                final Double value = extractScoreValue(entry);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        if (scoreObj instanceof JsonObject) {
            final JsonObject obj = (JsonObject) scoreObj;
            Double value = extractScoreValue(obj.get("score"));
            if (value != null) {
                return value;
            }
            value = extractScoreValue(obj.get("tournesol_score"));
            if (value != null) {
                return value;
            }
            value = extractScoreValue(obj.get("total_score"));
            if (value != null) {
                return value;
            }
            value = extractScoreValue(obj.get("value"));
            if (value != null) {
                return value;
            }
            value = extractScoreValue(obj.get("mean_score"));
            if (value != null) {
                return value;
            }
            return extractScoreValue(obj.get("mean"));
        }
        return null;
    }

    @Nullable
    private static String normalizeVideoId(@Nullable final String uid) {
        if (Utils.isNullOrEmpty(uid)) {
            return null;
        }
        if (uid.startsWith("yt:")) {
            return uid.substring(3);
        }
        return uid;
    }

    @Nullable
    private static String getString(@Nullable final JsonObject obj, final String key) {
        if (obj == null || !obj.has(key)) {
            return null;
        }
        final String value = obj.getString(key);
        return Utils.isNullOrEmpty(value) ? null : value;
    }

    private static long getLong(@Nullable final JsonObject obj, final String key,
                                final long defaultValue) {
        if (obj == null || !obj.has(key)) {
            return defaultValue;
        }
        final Object value = obj.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    @Nullable
    private static String firstNonEmpty(@Nullable final String... values) {
        for (final String value : values) {
            if (!Utils.isNullOrEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private static DateWrapper parseUploadDate(@Nullable final String raw) {
        if (Utils.isNullOrEmpty(raw)) {
            return null;
        }
        try {
            return new DateWrapper(OffsetDateTime.parse(raw));
        } catch (final DateTimeParseException ignored) {
            return null;
        }
    }
}
