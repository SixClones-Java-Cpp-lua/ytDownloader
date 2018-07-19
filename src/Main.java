import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.*;
import java.util.regex.Pattern;

public class Main {

    private static String newline = System.getProperty("line.separator");
    private static final Logger log = Logger.getLogger(Main.class.getCanonicalName());
    private static final Level defaultLogLevelSelf = Level.FINER;
    private static final Level defaultLogLevel = Level.WARNING;
    private static final Logger rootlog = Logger.getLogger("");
    private static final String scheme = "https";
    private static final Pattern commaPattern = Pattern.compile(",");
    private static final char[] ILLEGAL_FILENAME_CHARACTERS = {'/', '\n', '\r', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':'};

    private static void usage(String error, boolean needToStop) {
        if (error != null) {
            System.err.println("Error: " + error);
        }
        if (needToStop) {
            System.exit(-1);
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length < 2) {
            usage("Missing video id. Extract from http://www.youtube.com/watch?v=VIDEO_ID", true);
        }
        try {
            assert args != null;

            setupLogging();

            log.fine("Starting");
            String outDir = "D:\\video à regarder";
            Charset encoding = Charset.forName("UTF-8");
            String userAgent = "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US; rv:1.9.2.13) Gecko/20101203 Firefox/3.6.13";
            int format = 18; // http://en.wikipedia.org/wiki/YouTube#Quality_and_codecs
            File outputDir = new File(outDir);

            if (args[0].equalsIgnoreCase("p")) {
                for (String id : getVideoOfPlaylist(args[1])) {
                    play(id, format, encoding, userAgent, outputDir);
                }
            } else if (args[0].equalsIgnoreCase("v")) {
                for (int i = 1; i < args.length; i++) {
                    String url = args[i];
                    String videoId = clearURL(url);
                    if (videoId != null) {
                        play(videoId, format, encoding, userAgent, outputDir);
                    } else {
                        usage("Une vidéo n'a pas été reconnu", false);
                    }
                }
            } else {
                usage("args[0] doit être égale à v ou p", true);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        log.fine("Finished");
    }

    private static List<String> getVideoOfPlaylist(String playlistId) throws Throwable {
        log.fine("Retrieving " + playlistId);

        List<NameValuePair> qParams = new ArrayList<>();
        qParams.add(new BasicNameValuePair("part", "contentDetails"));
        qParams.add(new BasicNameValuePair("maxResults", "50"));
        qParams.add(new BasicNameValuePair("playlistId", playlistId));
        qParams.add(new BasicNameValuePair("key", "AIzaSyAL7YE_8oGFPPWJ6pgQVbAYF-G7tsbf15I"));
        URI uri = getUri("www.googleapis.com/youtube/v3", "playlistItems", qParams);
    //https://www.googleapis.com/youtube/v3/playlistItems?part=contentDetails&maxResults=50&playlistId=   &key=AIzaSyAL7YE_8oGFPPWJ6pgQVbAYF-G7tsbf15I

        log.fine("Analysing " + uri.toASCIIString());

        URLConnection conn = uri.toURL().openConnection();
        conn.connect();
        InputStreamReader in = new InputStreamReader(conn.getInputStream());

        List<String> videoIds = new ArrayList<>();

        //JSONParser parser = new JSONParser();
        JSONObject info = new JSONObject(scanWebJson(in));
        JSONArray videoList = info.getJSONArray("items");


        videoList.forEach(o -> {
            JSONObject video = new org.json.JSONObject(o.toString());
            JSONObject videoDetail = video.getJSONObject("contentDetails");
            videoIds.add((videoDetail.getString("videoId")));
        });

        return videoIds;
    }

    private static String scanWebJson(InputStreamReader in) throws IOException {
        StringBuilder text = new StringBuilder();
        BufferedReader br = new BufferedReader(in);
        String line;
        while ((line = br.readLine()) != null) {
            text.append(line);
        }
        return text.toString();
    }

    private static String clearURL(String url) {
        if (url.startsWith("https://youtu.be/")) {//https://youtu.be/VIDEO_ID
            url = url.replace("https://youtu.be/", "");
            return url.split(Pattern.quote("?"))[0];
        } else {//www.youtube.com/watch?v=VIDEO_ID
            url = url.replace("https://www.youtube.com/watch?", "");
            for(String urlPart : url.split("&")) {
                if (urlPart.startsWith("v=")) {
                    return urlPart.replace("v=", "");
                }
            }
        }
        return null;
    }

    private static void play(String videoId, int format, Charset encoding, String userAgent, File outputDir) throws Throwable {
        log.fine("Retrieving " + videoId);
        List<NameValuePair> qParams = new ArrayList<>();
        qParams.add(new BasicNameValuePair("video_id", videoId));
        qParams.add(new BasicNameValuePair("fmt", "" + format));
        URI uri = getUri("www.youtube.com","get_video_info", qParams);

        CookieStore cookieStore = new BasicCookieStore();
        HttpContext localContext = new BasicHttpContext();
        localContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpGet httpget = new HttpGet(uri);
        httpget.setHeader("User-Agent", userAgent);

        log.finer("Executing " + uri);
        HttpResponse response = httpclient.execute(httpget, localContext);
        HttpEntity entity = response.getEntity();

        if (entity != null && response.getStatusLine().getStatusCode() == 200) {
            InputStream inStream = entity.getContent();
            String videoInfo = getStringFromInputStream(encoding, inStream);

            if (videoInfo != null && videoInfo.length() > 0) {
                List<NameValuePair> infoMap = URLEncodedUtils.parse(new Scanner(videoInfo).nextLine(), encoding);

                //SString token = null;
                String downloadUrl = null;
                String filename = videoId;

                for (NameValuePair pair : infoMap) {
                    String key = pair.getName();
                    String val = pair.getValue();

                    switch (key) {
                        /*case "token":
                            token = val;
                            break;*/
                        case "title":
                            filename = val;
                            break;
                        case "url_encoded_fmt_stream_map":
                            String[] formats = commaPattern.split(val);

                            String[] fmtAgrs = formats[0].split("&");
                            log.fine(formats[0]);
                            for (String url : fmtAgrs) {
                                if (url.startsWith("quality=")) {
                                    log.fine(url);
                                }
                                if (url.startsWith("url=")) {
                                    downloadUrl = url.replace("url=", "");
                                    log.fine(downloadUrl);
                                }
                            }
                            break;
                    }
                }

                filename = cleanFilename(filename);
                if (filename.length() == 0) {
                    filename = videoId;
                }

                filename += ".mp4";
                File outputFile = new File(outputDir, filename);

                if (downloadUrl != null) {
                    downloadWithHttpClient(userAgent, java.net.URLDecoder.decode(downloadUrl, "UTF-8"), outputFile);
                }
            }
        }
    }


    private static void downloadWithHttpClient(String userAgent, String downloadUrl, File outputFile) throws Throwable {
        HttpGet httpGet2 = new HttpGet(downloadUrl);
        httpGet2.setHeader("User-Agent", userAgent);

        log.finer("Executing " + httpGet2.getURI());
        HttpClient httpclient2 = HttpClientBuilder.create().build();
        HttpResponse response2 = httpclient2.execute(httpGet2);
        HttpEntity entity2 = response2.getEntity();
        if (entity2 != null && response2.getStatusLine().getStatusCode() == 200) {
            long length = entity2.getContentLength();
            InputStream inStream2 = entity2.getContent();
            log.finer("Writing " + length + " bytes to " + outputFile);
            if (outputFile.exists()) {
                outputFile = new File(outputFile.getName().replace(".mp4", "") + "(1).mp4");
            }
            try (FileOutputStream outStream = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[2048];
                int count;
                int nextPourcentage = 10;
                while ((count = inStream2.read(buffer)) != -1) {
                    outStream.write(buffer, 0, count);
                    if (count/length*100 >= nextPourcentage) {
                        System.out.print(" - ");
                        nextPourcentage += 10;
                    }
                }
                log.fine(outputFile.getName() + " writed !");
                outStream.flush();
            }
        }
    }

    private static String cleanFilename(String filename) {
        for (char c : ILLEGAL_FILENAME_CHARACTERS) {
            filename = filename.replace(c, '_');
        }
        return filename;
    }

    private static URI getUri(String host, String path, List<NameValuePair> qParams) throws URISyntaxException {
        return new URI(scheme, null, host, -1, "/" + path, URLEncodedUtils.format(qParams, "UTF-8"), null);
    }

    private static void setupLogging() {
        changeFormatter(new Formatter() {
            @Override
            public String format(LogRecord arg0) {
                return arg0.getMessage() + newline;
            }
        });
        explicitlySetAllLogging();
    }

    private static void changeFormatter(Formatter formatter) {
        Handler[] handlers = rootlog.getHandlers();
        for (Handler handler : handlers) {
            handler.setFormatter(formatter);
        }
    }

    private static void explicitlySetAllLogging() {
        rootlog.setLevel(Level.ALL);
        for (Handler handler : rootlog.getHandlers()) {
            handler.setLevel(defaultLogLevelSelf);
        }
        log.setLevel(Level.FINER);
        rootlog.setLevel(defaultLogLevel);
    }

    private static String getStringFromInputStream(Charset encoding, InputStream inStream) throws IOException {
        Writer writer = new StringWriter();

        char[] buffer = new char[1024];
        try {
            Reader reader = new BufferedReader(new InputStreamReader(inStream, encoding));
            int n;
            while ((n = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
        } finally {
            inStream.close();
        }
        return writer.toString();
    }
}
