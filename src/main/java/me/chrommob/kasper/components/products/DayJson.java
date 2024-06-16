package me.chrommob.kasper.components.products;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DayJson {
    private final Map<String, Product> ourProducts = new HashMap<>();
    private final Map<String, Product> otherProducts = new HashMap<>();

    public static DayJson fromFile(File ourProductsFile) {
        Gson gson = new Gson();
        try (BufferedReader reader = new BufferedReader(new FileReader(ourProductsFile))) {
            return gson.fromJson(reader, DayJson.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static class Product {
        private final String name;
        private final String price;
        private final String euroPrice;
        private final Map<String, List<SellData>> typeToSellData = new HashMap<>();

        public Product(String name, String price, String euroPrice) {
            this.name = name;
            this.price = price;
            this.euroPrice = euroPrice;
        }

        public void addSell(String type, boolean wasCard, boolean wasEuros, int quantity) {
            long timestamp = System.currentTimeMillis();
            List<SellData> sellData = typeToSellData.computeIfAbsent(type, k -> new java.util.ArrayList<>());
            sellData.add(new SellData(timestamp, new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(timestamp)), wasCard, wasEuros, quantity));
        }
    }

    static class SellData {
        private final long timestamp;
        private final String readableTimestamp;
        private final boolean wasCard;
        private final boolean wasEuros;
        private final int quantity;
        public SellData(long timestamp, String readableTimestamp, boolean wasCard, boolean wasEuros, int quantity) {
            this.timestamp = timestamp;
            this.readableTimestamp = readableTimestamp;
            this.wasCard = wasCard;
            this.wasEuros = wasEuros;
            this.quantity = quantity;
        }
    }

    public void addProduct(String name, String price, String euroPrice, ProductType type) {
        Product product = new Product(name, price, euroPrice);
        if (type == ProductType.OURS) {
            ourProducts.put(name, product);
        } else {
            otherProducts.put(name, product);
        }
    }

    public void addSell(String name, String type, boolean wasCard, boolean wasEuros, int quantity) {
        Product product = ourProducts.get(name);
        if (product != null) {
            product.addSell(type, wasCard, wasEuros, quantity);
        } else {
            throw new RuntimeException("Product not found: " + name);
        }
    }
}
