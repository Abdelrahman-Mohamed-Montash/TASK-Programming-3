package sample.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import sample.model.Medicine;
import sample.service.MedicineService;
import sample.view.AddMedicineView;
import sample.view.EditMedicineView;
import sample.view.MedicineView;

import java.util.Optional;

public class MedicineController {

    private final MedicineView view;
    private final MedicineService service;
    private final ObservableList<Medicine> allMedicines;

    public MedicineController() {

        view = new MedicineView();
        service = new MedicineService();
        allMedicines = FXCollections.observableArrayList(
                service.loadMedicines()
        );

        view.getMedicineTable().setItems(allMedicines);
        view.updateStatistics(allMedicines);

        setupSearch();
        setupNavigationButtons();
        setupAddButton();
        setupEditButton();
        setupKeyboardEvents();
        setupMouseEvents();
    }

    // =========================================================
    // SEARCH
    // =========================================================

    private void setupSearch() {

        view.getSearchField().textProperty().addListener(
                (observable, oldValue, newValue) ->
                        filterMedicines(newValue)
        );

        view.getSearchButton().setOnAction(event ->
                filterMedicines(view.getSearchField().getText())
        );
    }

    private void filterMedicines(String searchText) {

        String search = searchText == null
                ? ""
                : searchText.trim().toLowerCase();

        ObservableList<Medicine> filtered =
                FXCollections.observableArrayList();

        for (Medicine medicine : allMedicines) {
            if (medicine.getName().toLowerCase().contains(search)) {
                filtered.add(medicine);
            }
        }

        view.getMedicineTable().setItems(filtered);
        view.getMedicineTable().refresh();
        view.updateStatistics(filtered);
    }

    private void showAllMedicines() {

        view.getSearchField().clear();
        view.getMedicineTable().setItems(allMedicines);
        view.getMedicineTable().getSelectionModel().clearSelection();
        view.getMedicineTable().refresh();
        view.updateStatistics(allMedicines);
    }

    // =========================================================
    // NAVIGATION AND WORKFLOW BUTTONS
    // =========================================================

    private void setupNavigationButtons() {

        view.getHomeButton().setOnAction(event -> reloadMedicines());

        view.getMedicinesButton().setOnAction(event -> {
            showAllMedicines();
            view.getSearchField().requestFocus();
        });

        view.getSalesButton().setOnAction(event -> recordSale());

        view.getReportsButton().setOnAction(event -> showInventoryReport());

        view.getSettingsButton().setOnAction(event -> changeLowStockThreshold());

        view.getLogoutButton().setOnAction(event -> logout());
    }

    private void reloadMedicines() {

        allMedicines.setAll(service.loadMedicines());
        showAllMedicines();
    }

    private void recordSale() {

        Medicine selectedMedicine = getSelectedMedicine("record a sale");
        if (selectedMedicine == null) {
            return;
        }

        if (selectedMedicine.getQuantity() == 0) {
            showAlert(Alert.AlertType.WARNING,
                    "Out of Stock",
                    "The selected medicine has no stock available to sell.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Record Sale");
        dialog.setHeaderText(selectedMedicine.getName());
        dialog.setContentText("Quantity to sell:");

        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) {
            return;
        }

        try {
            int soldQuantity = Integer.parseInt(result.get().trim());
            if (soldQuantity <= 0) {
                throw new NumberFormatException();
            }
            if (soldQuantity > selectedMedicine.getQuantity()) {
                showAlert(Alert.AlertType.WARNING,
                        "Insufficient Stock",
                        "Only " + selectedMedicine.getQuantity()
                                + " unit(s) are available.");
                return;
            }

            Medicine updatedMedicine = new Medicine(
                    selectedMedicine.getName(),
                    selectedMedicine.getPrice(),
                    selectedMedicine.getQuantity() - soldQuantity
            );

            if (!service.updateMedicine(selectedMedicine, updatedMedicine,
                    occurrenceOf(selectedMedicine))) {
                showStorageError();
                return;
            }

            replaceInMemory(selectedMedicine, updatedMedicine);
            showAlert(Alert.AlertType.INFORMATION,
                    "Sale Recorded",
                    soldQuantity + " unit(s) of " + selectedMedicine.getName()
                            + " sold. Total: "
                            + (selectedMedicine.getPrice() * soldQuantity));

        } catch (NumberFormatException exception) {
            showAlert(Alert.AlertType.ERROR,
                    "Invalid Quantity",
                    "Enter a positive whole-number quantity.");
        }
    }

    private void showInventoryReport() {

        int totalItems = allMedicines.size();
        int availableItems = 0;
        int lowStockItems = 0;
        int outOfStockItems = 0;
        int totalUnits = 0;
        double inventoryValue = 0;

        for (Medicine medicine : allMedicines) {
            int quantity = medicine.getQuantity();
            totalUnits += quantity;
            inventoryValue += medicine.getPrice() * quantity;

            if (quantity == 0) {
                outOfStockItems++;
            } else {
                availableItems++;
                if (quantity <= view.getLowStockThreshold()) {
                    lowStockItems++;
                }
            }
        }

        showAlert(Alert.AlertType.INFORMATION,
                "Inventory Report",
                "Medicines: " + totalItems
                        + "\nAvailable: " + availableItems
                        + "\nLow stock: " + lowStockItems
                        + "\nOut of stock: " + outOfStockItems
                        + "\nUnits in stock: " + totalUnits
                        + "\nInventory value: " + inventoryValue);
    }

    private void changeLowStockThreshold() {

        TextInputDialog dialog = new TextInputDialog(
                String.valueOf(view.getLowStockThreshold())
        );
        dialog.setTitle("Settings");
        dialog.setHeaderText("Low-stock threshold");
        dialog.setContentText("Show Low Stock when quantity is at or below:");

        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) {
            return;
        }

        try {
            int threshold = Integer.parseInt(result.get().trim());
            if (threshold < 0) {
                throw new NumberFormatException();
            }

            view.setLowStockThreshold(threshold);
            view.getMedicineTable().refresh();
            filterMedicines(view.getSearchField().getText());

        } catch (NumberFormatException exception) {
            showAlert(Alert.AlertType.ERROR,
                    "Invalid Setting",
                    "The low-stock threshold must be a whole number of zero or more.");
        }
    }

    private void logout() {

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Logout");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Do you want to close the application?");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Platform.exit();
        }
    }

    // =========================================================
    // ADD MEDICINE
    // =========================================================

    private void setupAddButton() {
        view.getAddButton().setOnAction(event -> addMedicine());
    }

    private void addMedicine() {

        AddMedicineView addView = new AddMedicineView();
        addView.getSaveButton().setOnAction(event -> saveNewMedicine(addView));
        addView.getCancelButton().setOnAction(event -> addView.getStage().close());
        addView.show();
    }

    private void saveNewMedicine(AddMedicineView addView) {

        Medicine medicine = readMedicine(
                addView.getNameField().getText(),
                addView.getPriceField().getText(),
                addView.getQuantityField().getText()
        );
        if (medicine == null) {
            return;
        }

        if (!service.addMedicine(medicine)) {
            showStorageError();
            return;
        }

        allMedicines.add(medicine);
        showAllMedicines();
        addView.getStage().close();
        showAlert(Alert.AlertType.INFORMATION,
                "Success", "Medicine added successfully.");
    }

    // =========================================================
    // EDIT MEDICINE
    // =========================================================

    private void setupEditButton() {
        view.getEditButton().setOnAction(event -> editSelectedMedicine());
    }

    private void editSelectedMedicine() {

        Medicine selectedMedicine = getSelectedMedicine("edit it");
        if (selectedMedicine == null) {
            return;
        }

        EditMedicineView editView = new EditMedicineView(selectedMedicine);
        editView.getSaveButton().setOnAction(event ->
                saveEditedMedicine(selectedMedicine, editView)
        );
        editView.getCancelButton().setOnAction(event -> editView.getStage().close());
        editView.show();
    }

    private void saveEditedMedicine(Medicine oldMedicine,
                                    EditMedicineView editView) {

        Medicine updatedMedicine = readMedicine(
                editView.getNameField().getText(),
                editView.getPriceField().getText(),
                editView.getQuantityField().getText()
        );
        if (updatedMedicine == null) {
            return;
        }

        if (!service.updateMedicine(oldMedicine, updatedMedicine,
                occurrenceOf(oldMedicine))) {
            showStorageError();
            return;
        }

        replaceInMemory(oldMedicine, updatedMedicine);
        editView.getStage().close();
        showAlert(Alert.AlertType.INFORMATION,
                "Success", "Medicine updated successfully.");
    }

    private Medicine readMedicine(String nameText,
                                  String priceText,
                                  String quantityText) {

        String name = nameText == null ? "" : nameText.trim();
        if (name.isEmpty() || name.contains(",")) {
            showAlert(Alert.AlertType.ERROR,
                    "Invalid Data",
                    "Medicine name cannot be empty or contain a comma.");
            return null;
        }

        try {
            double price = Double.parseDouble(priceText.trim());
            int quantity = Integer.parseInt(quantityText.trim());

            if (!Double.isFinite(price) || price < 0) {
                showAlert(Alert.AlertType.ERROR,
                        "Invalid Data", "Price must be a non-negative number.");
                return null;
            }
            if (quantity < 0) {
                showAlert(Alert.AlertType.ERROR,
                        "Invalid Data", "Quantity cannot be negative.");
                return null;
            }

            return new Medicine(name, price, quantity);

        } catch (NumberFormatException exception) {
            showAlert(Alert.AlertType.ERROR,
                    "Invalid Data",
                    "Price must be a number and quantity must be an integer.");
            return null;
        }
    }

    private Medicine getSelectedMedicine(String action) {

        Medicine selectedMedicine = view.getMedicineTable()
                .getSelectionModel().getSelectedItem();
        if (selectedMedicine == null) {
            showAlert(Alert.AlertType.WARNING,
                    "No Medicine Selected",
                    "Please select a medicine first to " + action + ".");
        }
        return selectedMedicine;
    }

    private int occurrenceOf(Medicine target) {

        int occurrence = 0;
        for (Medicine medicine : allMedicines) {
            if (medicine == target) {
                return occurrence;
            }
            if (medicine.sameValuesAs(target)) {
                occurrence++;
            }
        }
        return 0;
    }

    private void replaceInMemory(Medicine oldMedicine,
                                 Medicine updatedMedicine) {

        int index = allMedicines.indexOf(oldMedicine);
        if (index >= 0) {
            allMedicines.set(index, updatedMedicine);
        }

        String searchText = view.getSearchField().getText();
        filterMedicines(searchText);
    }

    private void showStorageError() {
        String reason = service.getLastError();
        showAlert(Alert.AlertType.ERROR,
                "Unable to Save",
                reason.isEmpty()
                        ? "The medicine data could not be saved."
                        : reason);
    }

    // =========================================================
    // INPUT CONVENIENCE
    // =========================================================

    private void setupMouseEvents() {
        view.getSearchField().setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                view.getSearchField().clear();
            }
        });
    }

    private void setupKeyboardEvents() {
        view.getSearchField().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                view.getSearchField().clear();
            }
        });
    }

    private void showAlert(Alert.AlertType type,
                           String title,
                           String message) {

        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public MedicineView getView() {
        return view;
    }
}
