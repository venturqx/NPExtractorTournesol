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
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.Utils;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
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

    public TournesolKioskExtractor(final StreamingService service,
                                   final ListLinkHandler linkHandler,
                                   final String kioskId) {
        super(service, linkHandler, kioskId);
        // Default behavior: 30 days ago, filtered by fr, en, es
        this.languages = Arrays.asList("fr", "en", "es");
        this.dateGte = calculateDaysAgo(30);
    }

    private String calculateDaysAgo(int days) {
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -days);
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(cal.getTime());
    }

    public void setLanguages(final List<String> languages) {
        this.languages = languages;
    }

    public void setDateGte(final String dateGte) {
        this.dateGte = dateGte;
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
        
        final String apiUrl = urlBuilder.toString();

        final String response = downloader.get(apiUrl, Collections.singletonMap("Accept",
                Collections.singletonList("application/json"))).responseBody();

        try {
            initialData = JsonParser.object().from(response);
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse Tournesol API response", e);
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return "Tournesol Recommendations";
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws ParsingException {
        final StreamInfoItemsCollector collector =
                new StreamInfoItemsCollector(getServiceId());

        if (initialData.has("results")) {
            final JsonArray results = initialData.getArray("results");
            
            for (final Object resultObj : results) {
                if (resultObj instanceof JsonObject) {
                    final JsonObject result = (JsonObject) resultObj;
                    final JsonObject entity = result.getObject("entity");

                    if (entity != null) {
                        String videoId = entity.getString("uid");
                        final Object metadataObj = entity.get("metadata");
                        JsonObject metadata = null;
                        if (metadataObj instanceof JsonObject) {
                            metadata = (JsonObject) metadataObj;
                        }

                        // Fallback logic for video ID
                        if (metadata != null && metadata.has("video_id")
                                && (Utils.isNullOrEmpty(videoId) || !videoId.matches("[a-zA-Z0-9_-]{11}"))) {
                            videoId = metadata.getString("video_id");
                        }

                        String title = null;
                        if (entity.has("name")) {
                            title = entity.getString("name");
                        }
                        
                        String uploader = null;
                        long duration = -1;
                        String thumbnail = null;

                        if (metadata != null) {
                            if (metadata.has("title")
                                    && !Utils.isNullOrEmpty(metadata.getString("title"))) {
                                title = metadata.getString("title");
                            } else if (metadata.has("name")
                                    && !Utils.isNullOrEmpty(metadata.getString("name"))) {
                                title = metadata.getString("name");
                            }

                            if (metadata.has("uploader")) {
                                uploader = metadata.getString("uploader");
                            }
                            if (metadata.has("duration")) {
                                duration = metadata.getLong("duration");
                            }
                        }

                        if (!Utils.isNullOrEmpty(videoId) && !Utils.isNullOrEmpty(title)) {
                            final String url = "https://www.youtube.com/watch?v=" + videoId;
                            final String finalUploader = uploader;
                            final long finalDuration = duration;
                            final String finalTitle = title;

                            // Construct thumbnail URL (mqdefault is standard for lists)
                            thumbnail = "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
                            final String finalThumbnail = thumbnail;

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
                                    return -1;
                                }

                                @Override
                                public String getUploaderName() throws ParsingException {
                                    return finalUploader;
                                }

                                @Override
                                public String getUploaderUrl() throws ParsingException {
                                    return null;
                                }

                                @Override
                                public boolean isUploaderVerified() throws ParsingException {
                                    return false;
                                }

                                @Nullable
                                @Override
                                public String getTextualUploadDate() throws ParsingException {
                                    return null;
                                }

                                @Nullable
                                @Override
                                public DateWrapper getUploadDate() throws ParsingException {
                                    return null;
                                }

                                @Nonnull
                                @Override
                                public String getName() throws ParsingException {
                                    return finalTitle;
                                }

                                @Nonnull
                                @Override
                                public String getUrl() throws ParsingException {
                                    return url;
                                }

                                @Nonnull
                                @Override
                                public List<Image> getThumbnails() throws ParsingException {
                                    return Collections.singletonList(new Image(finalThumbnail,
                                            Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN,
                                            Image.ResolutionLevel.UNKNOWN));
                                }
                            });
                        }
                    }
                }
            }
        }

        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        return InfoItemsPage.emptyPage();
    }
}
