# -*- coding: utf-8 -*-
import json
from PIL import Image
import numpy as np

data = json.load(open(r"C:\testda\foodcatalogservice\src\main\resources\catalog\nin-bulk-staging-20261125\ocr-tasks\batch-0039.json", encoding="utf-8"))
for t in data["tasks"]:
    code = t["code"]
    im = Image.open(t["imagePath"]).convert("RGB")
    arr = np.array(im)
    h, w, _ = arr.shape
    # badge only in bottom right
    badge_region = arr[int(h*0.65):, int(w*0.55):]
    cyan = (badge_region[:,:,0] < 50) & (badge_region[:,:,1] > 140) & (badge_region[:,:,2] > 180)
    cy, cx = np.where(cyan)
    b_top = int(h*0.65) + cy.min() if len(cy) else int(h*0.75)
    
    # top region above badge and table
    limit_y = min(b_top - 5, int(h * 0.72))
    top_region = arr[:limit_y, :]
    mask = np.any(top_region < 240, axis=2)
    # also mask out bottom left table if present (y > h*0.65)
    mask[int(h*0.68):, :int(w*0.45)] = False
    rows = np.where(mask.any(axis=1))[0]
    cols = np.where(mask.any(axis=0))[0]
    if len(rows) and len(cols):
        box = [max(0, int(cols[0])-5), max(0, int(rows[0])-5), min(w, int(cols[-1])+5), min(limit_y, int(rows[-1])+5)]
    else:
        box = [0, 0, w, limit_y]
        
    crop = im.crop(box)
    c_arr = np.array(crop)
    # check badge bleed in crop
    c_cyan = (c_arr[:,:,0] < 50) & (c_arr[:,:,1] > 140) & (c_arr[:,:,2] > 180)
    # only count if in bottom-right of crop
    c_h, c_w, _ = c_arr.shape
    br_cyan = c_cyan[int(c_h*0.7):, int(c_w*0.6):]
    print(f'    "{code}": {box}, # br_cyan: {np.sum(br_cyan)}')
