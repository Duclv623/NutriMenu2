"""Apply the reviewed team snapshot while retaining local integration policies."""
from pathlib import Path
import json, re, shutil

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / '.build-cache/upstream-760f0663'
if (WORK/'applied.json').exists():
    raise SystemExit('Snapshot already integrated; later corrections must not be overwritten.')
pattern = re.compile(r'<<<<<<< local\n(.*?)=======\n(.*?)>>>>>>> upstream\n?', re.S)

def resolve(text, choices):
    blocks = list(pattern.finditer(text))
    assert len(blocks) == len(choices), (len(blocks), len(choices))
    it = iter(choices)
    def replace(match):
        choice = next(it)
        return match[1] if choice == 'local' else match[2] if choice == 'remote' else match[1]+match[2] if choice == 'both' else choice(match[1],match[2])
    return pattern.sub(replace,text)

for row in json.loads((WORK/'merge-plan.json').read_text('utf8')):
    path = row['path']; name = Path(path).name
    text = (WORK/'merged'/path).read_text('utf-8-sig')
    if row['status']=='conflict':
        if name in ('README.md','loadPageData.ts'):
            text = (WORK/'before'/path).read_text('utf-8-sig')
        elif name == 'styles.css':
            base = Path('C:/testda/NutriMenu2')/path
            text = (WORK/'before'/path).read_text('utf8') + (WORK/'remote'/path).read_text('utf8')[len(base.read_text('utf8')):]
        elif name == 'DishRepository.java': text = resolve(text,['both'])
        elif name == 'GenerateDailyMealRequest.java': text = resolve(text,['local'])
        elif name == 'MealPlan.java': text = resolve(text,['both'])
        elif name == 'MealPlanMapper.java':
            text = resolve(text,[lambda a,b:b.replace('items, raw.getSolverStatus()', 'items, raw.getPlanningMode().name(), raw.getSolverStatus()')])
        elif name == 'CheckedMealChangeService.java':
            text = resolve(text,[lambda a,b:a.replace('request.getContraindications(), request.getExcludedDishIds()', 'policy.contraindications(), policy.excludedDishIds()'),lambda a,b:a.replace('request.getExcludedDishIds()', 'policy.excludedDishIds()').replace('request.getContraindications()', 'policy.contraindications()'),'local',lambda a,b:a.replace('private boolean','private static boolean')])
        elif name == 'MealOptimizerService.java':
            def diagnosis(a,b):
                return b[:b.index('        List<MealSlotTemplate>')] + a.replace('null, req.getMaxGiAllowed(), req.getPatientContraindications(), Set.of(), mealSlotsOf(slots)', 'userId, req.getMaxGiAllowed(), req.getPatientContraindications(), req.getExcludedDishIds(), mealSlotsOf(slots), req.getPlanningMode()')
            def edits(a,b): return b+a[a.index('        NewSourceCatalogPolicy'):]
            text=resolve(text,[diagnosis,edits,edits,lambda a,b:b+a[a.index('        candidates = NewSourceCatalogPolicy'):]])
        elif name == 'FoodDetail.tsx':
            def imports(a,b):
                return a.replace('import { foodService } from "../../../features/catalog/services/foodService";\n','') + b.replace('import { number } from "../../../utils";\n','').replace('import { Alert, Badge, Button, FoodMark, Modal, Nutrition } from "../../../components/common/ui";\n','').replace('ActionForm, serviceLabel','ActionForm')
            def content(a,b):
                metadata=a[a.index('  let sourceAnnotations'):a.index('  async function toggle')]
                return b.replace('  const tools:',metadata+'  const tools:')
            def buttons(a,b):
                return a[:a.index('        <Button')]+b[b.index('        <div className="actions">'):].replace('{favorite ? "Đã yêu thích" : "Yêu thích"}','{favorite ? "Bỏ yêu thích" : "Yêu thích"}').replace('aria-pressed={favorite}', 'aria-pressed={favorite}\n            aria-label={favorite ? `Bỏ yêu thích ${food.name}` : `Yêu thích ${food.name}`}').replace('disabled={busy || disliked}','disabled={busy}').replace('save(food, "dislike")','save(food, disliked ? null : "dislike")').replace('"Đã đánh dấu không thích"','"Bỏ không thích"')
            text=resolve(text,['remote',imports,content,buttons,'local'])
        elif name == 'FoodLibraryPage.tsx':
            def actions(a,b):
                return b.replace('          API_MODE', '          API_MODE',1).replace('<>\n              <Button','<>\n              <Badge tone="neutral">{catalogFoods.length} món trong danh mục</Badge>\n              <Button',1)
            text=resolve(text,[lambda a,b:a.replace('import { foodService } from "../../../features/catalog/services/foodService";','import { serviceAction } from "../../../lib/serviceActions";'),'remote','remote',lambda a,b:b.replace('w.foods.map','catalogFoods.map').replace('  const categories',a[a.index('  const catalogFoods'):a.index('  const categories')]+'  const categories'),lambda a,b:b+a[a.index('          (source'):],'remote',actions,'remote','local',lambda a,b:a+b[b.index('                    {w.disliked'):]])
            text=text.replace('favoritesOnly,\n    giLevel,','favoritesOnly,\n    source,\n    giLevel,')
        elif name == 'MealPlannerPage.tsx':
            choices=['local','remote','remote','remote','local','local','local','local','local','local', 'both','local','local','local','local','local']
            # Modal tail is resolved separately below; retain the validated personal generation flow.
            blocks=list(pattern.finditer(text))
            choices=[]
            for i,m in enumerate(blocks):
                a,b=m[1],m[2]
                if 'import { mealPlanService' in b or 'import { FoodDetail' in b: choices.append('remote')
                elif 'update((s)' in b: choices.append('remote')
                elif '<p className="fineprint">' in a: choices.append('both')
                else: choices.append('local')
            text=resolve(text,choices)
        elif name == 'api.ts': text=resolve(text,[lambda a,b:b.replace('/chat|meal-plans\\/tools|^\\/admin\\//','/chat|generate-weekly|meal-plans\\/tools|^\\/admin\\//')])
        elif name == 'UserDataProvider.tsx':
            text=resolve(text,['local']).replace('async (force = false) =>','async (force = false, background = false) =>').replace('const reload = useCallback(() => load(true), [load]);','const reload = useCallback((background = false) => load(true, background), [load]);')
        elif name == 'types.ts': text=resolve(text,['both','both'])
        else: raise ValueError(path)
    assert not pattern.search(text), path
    dest=ROOT/path; dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(text,encoding='utf8',newline='\n')
    if row['previous']!=path:
        old=ROOT/row['previous']
        assert old.resolve().is_relative_to(ROOT.resolve())
        old.unlink()
print('Applied 37 upstream files; baseline and before snapshots retained.')
