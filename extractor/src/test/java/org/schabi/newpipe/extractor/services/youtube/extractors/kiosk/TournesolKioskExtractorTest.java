package org.schabi.newpipe.extractor.services.youtube.extractors.kiosk;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.schabi.newpipe.extractor.NewPipe;

public class TournesolKioskExtractorTest {

    @Test
    public void testFetchPage() throws IOException, ExtractionException {
        // mock json response
        final String jsonResponse = "{\n" +
                "  \"results\": [\n" +
                "    {\n" +
                "      \"score\": 95.5,\n" +
                "      \"entity\": {\n" +
                "        \"uid\": \"dQw4w9WgXcQ\",\n" +
                "        \"name\": \"Rick Astley - Never Gonna Give You Up\",\n" +
                "        \"metadata\": {\n" +
                "          \"title\": \"Rick Astley - Never Gonna Give You Up\",\n" +
                "          \"uploader\": \"RickAstleyVEVO\",\n" +
                "          \"duration\": 213,\n" +
                "          \"views\": 1000000000\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        // mock downloader that returns the mock json response
        Downloader mockDownloader = new Downloader() {
            @Override
            public Response execute(Request request) {
                if (request.url().contains("tournesol.app")) {
                    return new Response(200, "OK", Collections.emptyMap(), jsonResponse, request.url());
                }
                return new Response(404, "Not Found", Collections.emptyMap(), "", request.url());
            }
        };

        // initialize extractor
        NewPipe.init(mockDownloader);
        TournesolKioskExtractor extractor = new TournesolKioskExtractor(
                ServiceList.YouTube,
                new ListLinkHandler("https://api.tournesol.app/polls/videos/recommendations/",
                        "https://api.tournesol.app/polls/videos/recommendations/",
                        "Tournesol",
                        Collections.emptyList(), ""),
                "Tournesol"
        );

        // fetch and get items
        extractor.onFetchPage(mockDownloader); 
        List<StreamInfoItem> items = extractor.getInitialPage().getItems();

        // verify items
        assertNotNull(items);
        assertFalse(items.isEmpty());
        assertEquals(1, items.size());
        StreamInfoItem item = items.get(0);
        assertEquals("Rick Astley - Never Gonna Give You Up", item.getName());
        assertEquals("RickAstleyVEVO", item.getUploaderName());
        assertEquals(213, item.getDuration());
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", item.getUrl());
        assertEquals(96L, item.getTournesolScore());
        assertNull(item.getShortDescription());
    }
}
