import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Compressor {

    private String folderFrom = "";
    private String folderTo = "";
    private String newFile = "";

    private int fileOrd = 0;
    private final String fileOrdStr = "file_include_order_";

    private String project = "";
    private String yuicompressor = "";

    private final Pattern eachNsRegex = Pattern.compile("((\"|')(\\w+\\.\\w+)*(\"|'))", Pattern.CASE_INSENSITIVE);
    private final Pattern nsRegex = Pattern.compile("(Ext\\.(ns|namespace)\\(((\"|')(\\w+\\.\\w+)*(\"|')(,\\s*)*)*\\);)", Pattern.CASE_INSENSITIVE);

    public Compressor() {
        //this.project = "c:\\Projects\\php\\kls5\\html\\";
        this.project = folderGetPath();
        this.yuicompressor = folderGetPath() + "/yuicompressor.jar";
    }

    public Map<String, Object> compress(String fileName, String minFileName, String charset) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", yuicompressor, fileName, "-o", minFileName, "--charset", charset);
        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String lastLine = "";
        String line;
        while ((line = reader.readLine()) != null) {
            lastLine = line;
        }

        fileOrd++;
        fileAppendContent(minFileName, "//" + fileOrdStr + fileOrd);

        int returnValue = process.waitFor();

        Map<String, Object> result = new HashMap<>();
        result.put("last_line", lastLine);
        result.put("return_value", returnValue);
        return result;
    }

    public Map<Integer, Map<String, Object>> run(String from, String to, String file, String charset) throws IOException, InterruptedException {
        fileOrd = 0;

        folderFrom = project + "/" + from;
        folderTo = project + "/" + to;
        if (!file.isEmpty()) {
            newFile = project + "/" + file;
        } else {
            newFile = "";
        }

        folderRemove(Paths.get(folderTo));
        folderCreate(Paths.get(folderTo));

        Map<Integer, Map<String, Object>> files = new TreeMap<>();
        folderScan(files, Paths.get(folderFrom), Paths.get(folderTo));

        for (Map.Entry<Integer, Map<String, Object>> entry : files.entrySet()) {
            Map<String, Object> fileMap = entry.getValue();
            if (fileMap.containsKey("files")) {
                List<Map<String, Object>> fileList = (List<Map<String, Object>>) fileMap.get("files");
                for (Map<String, Object> f : fileList) {
                    compress((String) f.get("file"), (String) f.get("file-min"), charset);
                }
            } else {
                compress((String) fileMap.get("file"), (String) fileMap.get("file-min"), charset);
            }
        }

        if (!newFile.isEmpty()) {
            fileCreate(newFile, "");
            Set<String> nses = new HashSet<>();
            for (Map.Entry<Integer, Map<String, Object>> entry : files.entrySet()) {
                Map<String, Object> fileMap = entry.getValue();
                if (fileMap.containsKey("files")) {
                    List<Map<String, Object>> fileList = (List<Map<String, Object>>) fileMap.get("files");
                    for (Map<String, Object> f : fileList) {
                        Object nsObj = f.get("ns");
                        if (nsObj instanceof List) {
                            List<String> nsList = (List<String>) nsObj;
                            for (String ns : nsList) {
                                if (!ns.isEmpty()) {
                                    nses.add("\"" + ns + "\"");
                                }
                            }
                        } else if (nsObj instanceof String) {
                            String ns = (String) nsObj;
                            if (!ns.isEmpty()) {
                                nses.add("\"" + ns + "\"");
                            }
                        }
                    }
                } else {
                    Object nsObj = fileMap.get("ns");
                    if (nsObj instanceof List) {
                        List<String> nsList = (List<String>) nsObj;
                        for (String ns : nsList) {
                            if (!ns.isEmpty()) {
                                nses.add("\"" + ns + "\"");
                            }
                        }
                    } else if (nsObj instanceof String) {
                        String ns = (String) nsObj;
                        if (!ns.isEmpty()) {
                            nses.add("\"" + ns + "\"");
                        }
                    }
                }
            }

            if (!nses.isEmpty()) {
                fileAppendContent(newFile, "Ext.ns(" + String.join(",", nses) + ");\n");
            }

            for (Map.Entry<Integer, Map<String, Object>> entry : files.entrySet()) {
                Map<String, Object> fileMap = entry.getValue();
                if (fileMap.containsKey("files")) {
                    List<Map<String, Object>> fileList = (List<Map<String, Object>>) fileMap.get("files");
                    for (Map<String, Object> f : fileList) {
                        fileAppendContent(newFile, nsRegex.matcher(fileGetContent((String) f.get("file-min"))).replaceAll("") + "\n");
                    }
                } else {
                    fileAppendContent(newFile, nsRegex.matcher(fileGetContent((String) fileMap.get("file-min"))).replaceAll("") + "\n");
                }
            }
        }

        return files;
    }

    private void fileCreate(String filename, String text) throws IOException {
        Files.write(Paths.get(filename), text.getBytes(StandardCharsets.UTF_8));
    }

    private void fileUnlink(String filename) throws IOException {
        Files.deleteIfExists(Paths.get(filename));
    }

    private void fileAppendContent(String filename, String text) throws IOException {
        Files.write(Paths.get(filename), text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
    }

    private String fileGetContent(String filename) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
    }

    private void folderCreate(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectory(path);
        }
    }

    private void folderRemove(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path)) {
                for (Path p : directoryStream) {
                    folderRemove(p);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private String folderGetPath() {
        return Paths.get(".").toAbsolutePath().normalize().toString();
    }

    private void folderScan(Map<Integer, Map<String, Object>> files, Path folderFrom, Path folderTo) throws IOException {
        if (Files.isDirectory(folderFrom)) {
            folderCreate(folderTo);
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(folderFrom)) {
                for (Path file : directoryStream) {
                    folderScan(files, file, folderTo.resolve(file.getFileName()));
                }
            }
        } else {
            List<String> exts = Arrays.asList("js", "css");
            String filename = folderFrom.getFileName().toString();
            String ext = filename.substring(filename.lastIndexOf('.') + 1);
            if (exts.contains(ext)) {
                String fileContent = fileGetContent(folderFrom.toString());
                Matcher matcher = Pattern.compile(fileOrdStr + "[0-9]*", Pattern.CASE_INSENSITIVE).matcher(fileContent);
                int ord;
                if (matcher.find()) {
                    ord = Integer.parseInt(matcher.group(0).substring(fileOrdStr.length()));
                } else {
                    ord = files.size();
                }

                Object namespace = null;
                if (ext.equals("js")) {
                    Matcher nsMatcher = nsRegex.matcher(fileContent);
                    while (nsMatcher.find()) {
                        Matcher eachNsMatcher = eachNsRegex.matcher(nsMatcher.group(0));
                        List<String> nses = new ArrayList<>();
                        while (eachNsMatcher.find()) {
                            String ns = eachNsMatcher.group(0).replaceAll("[\"']", "").trim();
                            nses.add(ns);
                        }
                        if (nses.size() > 1) {
                            namespace = nses;
                        } else if (nses.size() == 1) {
                            namespace = nses.get(0);
                        }
                    }
                }

                Map<String, Object> fileMap = new HashMap<>();
                fileMap.put("filename", filename);
                fileMap.put("file", folderFrom.toString());
                fileMap.put("file-min", folderTo.toString());
                fileMap.put("ns", namespace);

                if (files.containsKey(ord)) {
                    Map<String, Object> existingFileMap = files.get(ord);
                    List<Map<String, Object>> fileList = (List<Map<String, Object>>) existingFileMap.get("files");
                    if (fileList == null) {
                        fileList = new ArrayList<>();
                        Map<String, Object> existingSingleFileMap = new HashMap<>(existingFileMap);
                        existingFileMap.clear();
                        existingFileMap.put("files", fileList);
                        fileList.add(existingSingleFileMap);
                    }
                    fileList.add(fileMap);
                } else {
                    files.put(ord, fileMap);
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            Compressor compressor = new Compressor();
            compressor.run("js", "js-min", "project.js", "UTF-8");
            compressor.run("css", "css-min", "","windows-1251");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
