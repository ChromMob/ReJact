package me.chrommob.kasper.components.products;

import com.google.gson.Gson;
import me.chrommob.builder.html.tags.DivTag;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import static me.chrommob.builder.html.constants.GlobalAttributes.*;

public class ProductComponent extends DivTag {
    private final Gson gson = new Gson();
    private final List<DayJson.Product> ourProducts = new ArrayList<>();
    private final List<DayJson.Product> otherProducts = new ArrayList<>();

    public ProductComponent(File dataFolder) {
        super();

        ourProducts.add(new DayJson.Product("Placka", "30", "1.2"));
        ourProducts.add(new DayJson.Product("Vizitka", "12", "0.5"));
        ourProducts.add(new DayJson.Product("Skicák A3", "160", "6.3"));

        Calendar calendar = Calendar.getInstance();
        String year = String.valueOf(calendar.get(Calendar.YEAR));
        String month = String.valueOf(calendar.get(Calendar.MONTH) + 1);
        String day = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
        dataFolder = new File(dataFolder, "products/" + year + "/" + month);
        File todaysProductsFile = new File(dataFolder, day + ".json");
        todaysProductsFile.getParentFile().mkdirs();
        DayJson todaysProducts = null;
        if (todaysProductsFile.exists()) {
            todaysProducts = DayJson.fromFile(todaysProductsFile);
        }
        if (todaysProducts == null) {
            todaysProducts = new DayJson();
        }


        saveToFile(todaysProductsFile, todaysProducts);

        css("default", "display", "grid").css("grid-template-columns", "1fr 1fr").css("grid-gap", "10px");
        addChild(new ProductListComponent());
        addChild(new ProductListComponent());
    }

    private void saveToFile(File todaysProductsFile, DayJson todaysProducts) {
        try (FileWriter writer = new FileWriter(todaysProductsFile)) {
            writer.write(gson.toJson(todaysProducts));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class ProductListComponent extends DivTag {
        public ProductListComponent() {
            super();
            addAttribute(ID, "products");
            css("default", "display", "grid").css("grid-template-columns", "1fr 1fr").css("grid-gap", "10px");

        }
    }
}
