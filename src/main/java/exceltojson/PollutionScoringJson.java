package exceltojson;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

public class PollutionScoringJson {

    public static double[] parseDMS(String dms) {
        try {
            String[] parts = dms.split(" ");
            String latPart = parts[0];
            String lonPart = parts[1];

            double lat = convertPartToDecimal(latPart);
            double lon = convertPartToDecimal(lonPart);

            return new double[]{lat, lon};
        } catch (Exception e) {
            System.out.println("Error parsing DMS string: " + dms);
            return new double[]{0.0, 0.0};
        }
    }

    private static double convertPartToDecimal(String part) {
        part = part.replace("°", " ").replace("'", " ").replace("\"", " ");
        String[] tokens = part.trim().split("\s+");
        double degrees = Double.parseDouble(tokens[0]);
        double minutes = Double.parseDouble(tokens[1]);
        double seconds = Double.parseDouble(tokens[2]);
        double result = degrees + (minutes / 60) + (seconds / 3600);
        if (part.toUpperCase().contains("S") || part.toUpperCase().contains("W")) {
            result *= -1;
        }
        return result;
    }

    static Map<String, Double> thresholds = new HashMap<>() {
        {
            put("Chromium", 0.32);
            put("Cadmium", 0.21);
            put("Lead", 1.3);
            put("Nickel", 0.25);
            put("Copper", 0.45);
            put("Zinc", 1.6);
            put("Manganese", 1.2);
            put("Cobalt", 0.3);
            put("Barium", 1.0);
            put("Arsenic", 0.13);
            put("Dissolved Oxygen", 4.0); // Special case: inverse logic
        }
    };

    static Map<String, Double> weights = new HashMap<>() {
        {
            put("Chromium", 1.0);
            put("Cadmium", 1.2);
            put("Lead", 1.1);
            put("Nickel", 1.0);
            put("Copper", 0.9);
            put("Zinc", 0.8);
            put("Manganese", 0.8);
            put("Cobalt", 0.9);
            put("Barium", 0.9);
            put("Arsenic", 1.2);
            put("Dissolved Oxygen", 1.5);
        }
    };

    static Set<String> criticalMetals = Set.of("Chromium", "Cadmium", "Lead", "Copper", "Nickel", "Arsenic", "Barium", "Cobalt", "Manganese");

    public static void main(String[] args) throws IOException, InvalidFormatException {
        if (args.length < 1) {
            System.out.println("Usage: java PollutionScoringJson <folder_path>");
            return;
        }

        File folder = new File(args[0]);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".xlsx"));

        if (files == null) {
            System.out.println("No Excel files found in the folder.");
            return;
        }

        List<JSONObject> results = new ArrayList<>();

        for (File file : files) {
            String[] parts = file.getName().replace(".xlsx", "").split("_");
            int year = 0;
            int month = 0;
            try {
                for (String part : parts) {
                    if (part.matches("\\d{4}")) {
                        year = Integer.parseInt(part);
                    } else if (part.matches("\\d{2}")) {
                        month = Integer.parseInt(part);
                    }
                }
            } catch (Exception e) {
                System.out.println("Warning: Unable to parse year/month from filename: " + file.getName());
            }

            try (Workbook workbook = new XSSFWorkbook(file)) {
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    Map<String, Double> measurements = new HashMap<>();
                    String locationDMS = "";
                    double lat = 0, lon = 0;

                    try {
                        for (Row row : sheet) {
                            Cell paramCell = row.getCell(0);
                            Cell valueCell = row.getCell(1);
                            if (paramCell == null || valueCell == null) {
                                continue;
                            }

                            String param = paramCell.toString().trim();

                            if (param.equalsIgnoreCase("Location")) {
                                locationDMS = valueCell.toString();
                                double[] coords = parseDMS(valueCell.toString());
                                lat = coords[0];
                                lon = coords[1];
                            } else if (thresholds.containsKey(param)) {
                                String valStr = valueCell.toString().replace("<", "").trim();
                                if (valStr.contains("±")) {
                                    valStr = valStr.split("±")[0].trim();
                                }
                                if (valStr.equalsIgnoreCase("Not Detectable") || valStr.equalsIgnoreCase("Not Detected") || valStr.equalsIgnoreCase("Μή ανιχνεύσιμο")) {
                                    valStr = "0";
                                }
                                try {
                                    double val = Double.parseDouble(valStr);
                                    measurements.put(param, val);
                                } catch (NumberFormatException e) {
                                    if (!valStr.equals("0")) {
                                        System.out.println("Invalid value for parameter '" + param + "' in file: " + file.getName() + " / " + sheet.getSheetName());
                                        System.out.println("Value: '" + valStr + "' — Reason: " + e.getMessage());
                                    }
                                }
                            }
                        }

                        if (!measurements.isEmpty()) {
                            JSONObject json = new JSONObject();
                            json.put("year", year);
                            json.put("month", month);
                            json.put("location", locationDMS);
                            json.put("lat", lat);
                            json.put("lon", lon);
                            json.put("soft_score", calculateSoftScore(measurements));
                            json.put("hard_score", calculateHardScore(measurements));
                            results.add(json);
                        } else {
                            System.out.println("Skipped (no valid measurements): " + file.getName() + " / " + sheet.getSheetName());
                        }
                    } catch (Exception e) {
                        System.out.println("Error parsing sheet in file: " + file.getName() + " / " + sheet.getSheetName());
                        System.out.println("Reason: " + e.getMessage());
                    }
                }
            }
        }

        JSONArray outputArray = new JSONArray(results);
        try (FileWriter file = new FileWriter("/app/output/wp-content/uploads/pollution_scores.json")) {
            file.write(outputArray.toString(2));
            System.out.println("pollution_scores.json written successfully.");
            System.out.println("Total rows exported: " + results.size());
        }
    }

    public static int calculateHardScore(Map<String, Double> measurements) {
        int count = 0;
        for (String param : criticalMetals) {
            if (!thresholds.containsKey(param)) {
                continue;
            }
            double threshold = thresholds.get(param);
            if (measurements.containsKey(param) && measurements.get(param) > threshold) {
                count++;
            }
        }
        return count;
    }

    public static double calculateSoftScore(Map<String, Double> measurements) {
        double sum = 0;
        for (String param : thresholds.keySet()) {
            double threshold = thresholds.get(param);
            double weight = weights.getOrDefault(param, 1.0);
            if (measurements.containsKey(param)) {
                double value = measurements.get(param);
                double exceedance = 0;
                if (param.equals("Dissolved Oxygen")) {
                    // Inverse logic: score only when below threshold
                    if (value < threshold) {
                        exceedance = (threshold - value) / threshold;
                    }
                } else {
                    exceedance = Math.max(0, (value - threshold) / threshold);
                }
                sum += weight * exceedance;
            }
        }
        return Math.round(sum * 100.0) / 100.0;
    }
}
