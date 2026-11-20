package sample.service;

import sample.model.Medicine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class MedicineService {

    private final Path dataFile;
    private String lastError = "";

    public MedicineService() {
        dataFile = resolveDataFile();
    }

    private Path resolveDataFile() {

        Path[] candidates = {
                Path.of("Data", "medicines.txt"),
                Path.of("Week3", "Data", "medicines.txt")
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        return candidates[0];
    }

    // =========================================================
    // LOAD MEDICINES
    // =========================================================

    public List<Medicine> loadMedicines() {

        List<Medicine> medicines = new ArrayList<>();
        lastError = "";

        if (!Files.exists(dataFile)) {
            lastError = "Medicine data file was not found: " + dataFile;
            return medicines;
        }

        try (BufferedReader reader = Files.newBufferedReader(
                dataFile, StandardCharsets.UTF_8
        )) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                Medicine medicine = parseMedicine(line);
                if (medicine == null) {
                    lastError = "Invalid medicine data on line " + lineNumber + ".";
                    continue;
                }
                medicines.add(medicine);
            }

        } catch (IOException exception) {
            lastError = "Could not read medicine data: " + exception.getMessage();
        }

        return medicines;
    }

    // =========================================================
    // ADD / UPDATE MEDICINES
    // =========================================================

    public boolean addMedicine(Medicine medicine) {

        lastError = "";
        try {
            ensureDataDirectory();
            try (BufferedWriter writer = Files.newBufferedWriter(
                    dataFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                writer.write(toLine(medicine));
                writer.newLine();
            }
            return true;

        } catch (IOException exception) {
            lastError = "Could not save medicine data: " + exception.getMessage();
            return false;
        }
    }

    public boolean updateMedicine(Medicine oldMedicine,
                                  Medicine updatedMedicine,
                                  int occurrence) {

        List<Medicine> medicines = loadMedicines();
        if (!lastError.isEmpty()) {
            return false;
        }

        int matches = 0;
        for (int index = 0; index < medicines.size(); index++) {
            Medicine medicine = medicines.get(index);
            if (!medicine.sameValuesAs(oldMedicine)) {
                continue;
            }

            if (matches == occurrence) {
                medicines.set(index, updatedMedicine);
                return saveMedicines(medicines);
            }
            matches++;
        }

        lastError = "The selected medicine no longer exists in the data file.";
        return false;
    }

    private boolean saveMedicines(List<Medicine> medicines) {

        lastError = "";
        try {
            ensureDataDirectory();
            try (BufferedWriter writer = Files.newBufferedWriter(
                    dataFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                for (Medicine medicine : medicines) {
                    writer.write(toLine(medicine));
                    writer.newLine();
                }
            }
            return true;

        } catch (IOException exception) {
            lastError = "Could not save medicine data: " + exception.getMessage();
            return false;
        }
    }

    private void ensureDataDirectory() throws IOException {
        Path parent = dataFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private Medicine parseMedicine(String line) {

        String[] parts = line.split(",", -1);
        if (parts.length != 3) {
            return null;
        }

        try {
            String name = parts[0].trim();
            double price = Double.parseDouble(parts[1].trim());
            int quantity = Integer.parseInt(parts[2].trim());

            if (name.isEmpty() || !Double.isFinite(price)
                    || price < 0 || quantity < 0) {
                return null;
            }
            return new Medicine(name, price, quantity);

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String toLine(Medicine medicine) {
        return medicine.getName() + ","
                + medicine.getPrice() + ","
                + medicine.getQuantity();
    }

    public String getLastError() {
        return lastError;
    }
}
