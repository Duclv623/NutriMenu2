"""Create reviewed ingredient references; no fuzzy joins or inferred source zeros."""
import hashlib
import json
from pathlib import Path

from nin_ingredient_model import LINKS

REPO_ROOT = Path(__file__).resolve().parents[3]
ROOT = REPO_ROOT / "BackendNutriMenu"
FOLDER = ROOT / "foodcatalogservice/src/main/resources/catalog/nin-real-20261123"
FCT = REPO_ROOT / "ai-service/data/structured/fct2007/foods.json"
FCT_URL = "https://www.fao.org/fileadmin/templates/food_composition/documents/pdf/VTN_FCT_2007.pdf"
FIELD_NAMES = {"totalCarbG": "Glucid (Carbohydrate)", "fiberG": "Celluloza (Fiber)",
               "proteinG": "Protein", "fatG": "Lipid (Fat)", "cholesterolMg": "Cholesterol"}
BLACKLIST = {"4116", "4108"}  # Numeric identities differ between FCT2007 and the newer website.


def build():
    foods = {x["code"]: x for x in json.loads(FCT.read_text(encoding="utf-8"))}
    references = {
        "NIN_FCT2007": {"title": "Vietnamese Food Composition Table 2007, NIN / Ministry of Health",
                        "url": FCT_URL, "localExtractionSha256": hashlib.sha256(FCT.read_bytes()).hexdigest(),
                        "basis": "100 g edible portion", "zeroRule": "Only explicit numeric raw_value, never '-' or omitted cells.",
                        "fiberLimitation": "Celluloza is a cellulose/crude-fibre proxy, not a measured total dietary fibre equivalent."},
        "SAUROPUS_2022": {"title": "Effects of Storage, Preparation and Heat Treatment on Water-Soluble Vitamins and Minerals of Sauropus androgynous",
            "url": "https://www.foodandnutritionjournal.org/volume10number2/effects-of-storage-prepration-and-heat-treatment-on-water-soluble-vitamins-and-minerals-of-sauropus-androgynous/",
            "doi": "10.12944/CRNFSJ.10.2.23", "location": "Table 1, fresh leaves, Binh Duong, Vietnam",
            "observation": "Lipid 0.82 +/- 0.02 g / 100 g fresh weight; proxy across cultivation/preparation."},
        "NOAA_MACKEREL": {"title": "NOAA Fisheries: Atlantic mackerel, nutrition",
            "url": "https://www.fisheries.noaa.gov/species/atlantic-mackerel",
            "limitation": "Atlantic mackerel representative for Vietnamese unspecified mackerel species."},
        "USDA_SR28_PORK_FRANKFURTER": {"title": "USDA SR28: sausages and luncheon meats, NDB 07939 Frankfurter pork",
            "url": "https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR/SR28/reports/sr28fg07.pdf",
            "pdfPageOneBased": 161, "limitation": "Pork frankfurter representative for Vietnamese unspecified sausage formulation."},
        "GI_TABLES2008": {"title": "International tables of glycemic index and glycemic load values: 2008",
            "url": "https://pmc.ncbi.nlm.nih.gov/articles/PMC2584181/", "doi": "10.2337/dc08-1239",
            "note": "Recipe GI model uses means/proxies and authors' low-carbohydrate vegetable/dairy assignments; no NIN dish GI measured."},
        "USDA_SR28_SNACKS": {"title": "USDA SR28 snacks, NDB 19041 Pork skins, plain",
            "url": "https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg25.pdf", "pdfPageOneBased": 299},
        "USDA_SR28_SOUPS": {"title": "USDA SR28 soups, sauces and gravies",
            "url": "https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg06.pdf"},
        "USDA_SR27_GREEN_BEANS": {"title": "USDA SR27 vegetables: NDB 11052 Beans snap green raw",
            "url": "https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR27/reports/sr27fg11.pdf"},
        "MEXT_IWANORI": {"title": "Japan MEXT food composition database: 09007 Iwanori dried",
            "url": "https://fooddb.mext.go.jp/details/details.pl?ITEM_NO=9_09007_7&MODE=0",
            "limitation": "Dried Porphyra proxy for unspecified sushi seaweed; species and preparation differ."},
        "JECFA_MSG": {"title": "FAO/WHO JECFA monosodium L-glutamate specification, CAS 6106-04-3",
            "url": "https://www.fao.org/food/food-safety-quality/scientific-advice/jecfa/jecfa-additives/detail/en/c/141/"},
    }
    entries = {}
    for name, (code, group, _, limitation) in LINKS.items():
        row = {"sourceName": name, "nutrients": {}}
        food = foods.get(code) if code not in BLACKLIST else None
        if food:
            values = {x["name"]: x for x in food["nutrients"]}
            for field, nutrient_name in FIELD_NAMES.items():
                value = values.get(nutrient_name)
                if value and value.get("value") is not None and value.get("raw_value") not in (None, "", "-"):
                    row["nutrients"][field] = {
                        "value": value["value"], "referenceId": "NIN_FCT2007", "sourceCode": code,
                        "sourceName": food["name"], "sourceNameEn": food.get("name_en"),
                        "page": food["page"], "rawValue": value["raw_value"], "sourceNutrient": nutrient_name,
                        "method": "FCT_CELLULOSE_PROXY_ESTIMATE" if field == "fiberG" else "FCT2007_NAME_VERIFIED_INGREDIENT_PROXY",
                        "note": limitation,
                    }
            if "cả xương" in name or "nguyên con" in name or "cả vỏ" in name:
                discard = food.get("discard_percent")
                if discard is not None:
                    row["edibleFraction"] = round(1 - discard / 100, 6)
                    row["edibleFractionEvidence"] = {"method": "FCT2007_DISCARD_FRACTION_ESTIMATE", "referenceId": "NIN_FCT2007",
                        "sourceCode": code, "sourceName": food["name"], "discardPercent": discard,
                        "page": food["page"], "sourceUrl": FCT_URL,
                        "note": "Population reference fraction; not measured for this photographed portion."}
        entries[name] = row

    def add(name, field, value, ref, note, **extra):
        entries[name]["nutrients"][field] = {"value": value, "referenceId": ref,
            "method": "EXPLICIT_INGREDIENT_PROXY_ESTIMATE", "note": note, **extra}

    def fct_proxy(name, field, code, note):
        food = foods[code]
        value = next(n for n in food["nutrients"] if n["name"] == FIELD_NAMES[field])
        if value["value"] is None:
            raise ValueError("Proxy observation is missing")
        add(name, field, value["value"], "NIN_FCT2007", note, sourceCode=code,
            sourceName=food["name"], page=food["page"], rawValue=value["raw_value"])

    add("Rau ngót", "fatG", 0.82, "SAUROPUS_2022", "Fresh Sauropus leaf composition proxy; cultivar/storage and boiling retention unmeasured.")
    add("Cá thu", "cholesterolMg", 70, "NOAA_MACKEREL", "Different mackerel species nutrition proxy, raw edible portion.")
    add("Xúc xích", "cholesterolMg", 66, "USDA_SR28_PORK_FRANKFURTER", "Pork frankfurter formulation proxy; not measured Vietnamese sausage.", sourceCode="NDB_07939")
    fct_proxy("Cá bống (nguyên con)", "cholesterolMg", "8003", "Carp cholesterol proxy for goby; different freshwater fish, not species measurement.")
    fct_proxy("Thịt hến", "cholesterolMg", "8054", "Freshwater mussel proxy for freshwater clam meat; different species; source meat already edible.")
    fct_proxy("Nước luộc hến", "cholesterolMg", "8054", "HIGH_UNCERTAINTY_BROTH_PROXY: edible freshwater mussel concentration used for clam cooking broth without invented dilution. Broth is not flesh; transfer/concentration and batch size unmeasured. Not a laboratory observation or guaranteed upper bound.")
    for name in ("Thịt nạc vai", "Thịt lợn nạc vai"):
        for field in ("totalCarbG", "fiberG"):
            fct_proxy(name, field, "7017", "Lean pork reference zero for unprocessed pork shoulder; not exact cut measurement.")
    for field in ("totalCarbG", "fiberG"):
        fct_proxy("Thịt bò bắp", field, "7002", "Lean beef reference zero for unprocessed beef shank; not exact cut measurement.")
    # Website and FCT identifiers are linked by reviewed names, never number alone.
    for name, code in (("Dưa cải bẹ", "4112"), ("Nấm", "4126"), ("Cải chíp", "4015"),
                       ("Rau cải ngọt", "4016"), ("Ớt", "4061")):
        for field in FIELD_NAMES:
            observation = next((n for n in foods[code]["nutrients"] if n["name"] == FIELD_NAMES[field]), None)
            if observation and observation["value"] is not None:
                fct_proxy(name, field, code, "Name-reviewed food-form proxy; exact cultivar/species/preparation may differ.")
    fct_proxy("Dầu TV", "cholesterolMg", "6010", "Soybean oil cholesterol proxy for unspecified vegetable oil, not a source observation.")
    fct_proxy("Muối vừng", "cholesterolMg", "3020", "Sesame seed cholesterol proxy for sesame/salt mixture; unlisted ingredients not verified.")
    fct_proxy("Lạc rang dầu", "cholesterolMg", "11004", "Oil-roasted peanut cholesterol proxy for source roasted peanuts.")
    fct_proxy("Thịt cua", "fiberG", "8033", "Explicit marine crab fibre zero proxy for unspecified edible crab meat.")
    add("Bì lợn rán", "cholesterolMg", 95, "USDA_SR28_SNACKS", "Plain fried pork rind proxy; source frying medium and formulation unknown.", sourceCode="NDB_19041")
    for name in ("Bột canh", "Hạt nêm", "Bột nêm", "Gia vị"):
        add(name, "cholesterolMg", 13, "USDA_SR28_SOUPS", "Dry chicken bouillon proxy for unspecified seasoning blend; brand and animal content differ.", sourceCode="NDB_06080", pdfPageOneBased=679)
        add(name, "fiberG", 0, "USDA_SR28_SOUPS", "Explicit dry chicken bouillon fibre zero used as formulation proxy; not measured source seasoning.", sourceCode="NDB_06080", pdfPageOneBased=678)
    add("Dầu hào", "fiberG", 0.3, "USDA_SR28_SOUPS", "Ready-to-serve oyster sauce proxy, brand unknown.", sourceCode="NDB_06176", pdfPageOneBased=516)
    add("Đậu cove", "fatG", 0.22, "USDA_SR27_GREEN_BEANS", "Raw Phaseolus green bean fat proxy; boiling retention/yield unmeasured.", sourceCode="NDB_11052")
    add("Rong biển", "fiberG", 36.4, "MEXT_IWANORI", "Dried iwanori dietary fibre proxy for unspecified sushi nori; not measured recipe seaweed.", sourceCode="09007")
    add("Rong biển", "cholesterolMg", 30, "MEXT_IWANORI", "Dried iwanori cholesterol proxy; seaweed must not be assumed cholesterol-free.", sourceCode="09007")
    for field in ("totalCarbG", "fiberG", "cholesterolMg"):
        add("Mì chính", field, 0, "JECFA_MSG", "PURE_MSG_COMPOSITION_ASSUMPTION: pure monosodium glutamate salt contains no carbohydrate polymer/fibre/cholesterol; source assumed pure MSG, not seasoning blend.")
    fct_proxy("Ngũ vị hương", "fiberG", "13001", "Curry powder fibre proxy for an unspecified dry spice blend; not measured five-spice composition.")
    fct_proxy("Sả", "fiberG", "13009", "HIGH_UNCERTAINTY_AROMATIC_PLANT_PROXY: fresh galangal cellulose 3.7 g/100g used for 10 g fresh lemongrass; different plant/part, not measured lemongrass dietary fibre.")
    for field in ("totalCarbG", "fiberG", "cholesterolMg"):
        fct_proxy("Nước hàng", field, "12014", "PURE_SUGAR_CARAMEL_ASSUMPTION: sugar/water caramel representative, no milk or animal additives; concentration uncertain, pure sugar is carbohydrate upper proxy.")
    snapshot = {"schemaVersion": 1, "references": references, "ingredients": list(entries.values())}
    path = FOLDER / "ingredient-estimation-reference.json"
    path.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"ingredientReferences": len(entries), "path": str(path)}))


if __name__ == "__main__":
    build()
