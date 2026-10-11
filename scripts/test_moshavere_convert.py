import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("moshavere_convert", HERE / "moshavere_convert.py")
conv = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(conv)

REAL_TREE = conv.DEFAULT_TREE
REAL_CSV = conv.DEFAULT_CSV

TREE = """<html><body><ul id="myUL">
<li><span class="caret">فیزیک(1)</span><ul class="nested">
<li>الف(2)</li>
<li><span class="caret">ب(3)</span><ul class="nested"><li>پ(4)</li></ul></li>
</ul></li>
<li>عربی(5)</li>
</ul></body></html>"""

CSV = (
    "grade,group,title,track\n"
    "دوازدهم,g1,فیزیک پایه دوازدهم,ریاضی\n"
    "دوازدهم,g1,عربی,common\n"
    "دوازدهم,g2,عربی,common\n"
)


class Base(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.dir = Path(self._tmp.name)

    def write(self, name, text):
        p = self.dir / name
        p.write_text(text, encoding="utf-8")
        return p

    def build(self, tree=TREE, csv=CSV):
        return conv.build(self.write("t.html", tree), self.write("c.csv", csv))


class ConverterTest(Base):
    def test_depth_first_order_and_parents(self):
        bundle, _ = self.build()
        self.assertEqual([t["sourceCode"] for t in bundle["topics"]], ["1", "2", "3", "4", "5"])
        parents = {t["sourceCode"]: t["parentSourceCode"] for t in bundle["topics"]}
        self.assertEqual(parents, {"1": None, "2": "1", "3": "1", "4": "3", "5": None})
        self.assertEqual(bundle["topics"][0]["name"], "فیزیک")

    def test_sibling_sort_order(self):
        bundle, _ = self.build()
        order = {t["sourceCode"]: t["sortOrder"] for t in bundle["topics"]}
        self.assertEqual(order, {"1": 0, "5": 1, "2": 0, "3": 1, "4": 0})

    def test_duplicate_code_refused(self):
        bad = TREE.replace("(5)", "(2)")
        with self.assertRaises(conv.ConversionError):
            self.build(tree=bad)

    def test_duplicate_sibling_name_refused(self):
        bad = TREE.replace("عربی(5)", "فیزیک(5)")
        with self.assertRaises(conv.ConversionError):
            self.build(tree=bad)

    def test_node_without_code_refused(self):
        with self.assertRaises(conv.ConversionError):
            self.build(tree=TREE.replace("(5)", ""))

    def test_bad_grade_and_track_refused(self):
        with self.assertRaises(conv.ConversionError):
            self.build(csv="grade,group,title,track\n9,g,x,common\n")
        with self.assertRaises(conv.ConversionError):
            self.build(csv="grade,group,title,track\n12,g,x,other\n")

    def test_main_refuses_with_nonzero_exit_and_no_file(self):
        out = self.dir / "out.json"
        code = conv.main(["--tree", str(self.write("t.html", TREE.replace("(5)", "(2)"))),
                          "--csv", str(self.write("c.csv", CSV)), "--out", str(out)])
        self.assertNotEqual(code, 0)
        self.assertFalse(out.exists())

    def test_common_course_deduped_with_null_track(self):
        bundle, _ = self.build()
        arabic = [c for c in bundle["courses"] if c["name"] == "عربی"]
        self.assertEqual(len(arabic), 1)
        self.assertIsNone(arabic[0]["track"])
        self.assertEqual(arabic[0]["topicSourceCode"], "5")

    def test_track_course_gets_track_and_strips_paye_for_match(self):
        bundle, _ = self.build()
        physics = [c for c in bundle["courses"] if c["name"].startswith("فیزیک")]
        self.assertEqual(len(physics), 1)
        self.assertEqual(physics[0]["track"], "ریاضی")
        self.assertEqual(physics[0]["gradeLevel"], 12)
        self.assertEqual(physics[0]["topicSourceCode"], "1")

    def test_arabic_yeh_matches(self):
        csv = "grade,group,title,track\n12,g,عربي,common\n"
        bundle, report = self.build(csv=csv)
        self.assertEqual(bundle["courses"][0]["topicSourceCode"], "5")
        self.assertEqual(report, [])

    def test_ambiguous_match_reported_not_guessed(self):
        tree = TREE.replace("<li>عربی(5)</li>",
                            "<li>عربی(5)</li><li>عربي(6)</li>")
        bundle, report = self.build(tree=tree)
        arabic = [c for c in bundle["courses"] if c["name"] == "عربی"][0]
        self.assertIsNone(arabic["topicSourceCode"])
        self.assertTrue(any(line.startswith("AMBIGUOUS") for line in report))

    def test_no_match_reported(self):
        csv = "grade,group,title,track\n12,g,ناموجود,common\n"
        bundle, report = self.build(csv=csv)
        self.assertIsNone(bundle["courses"][0]["topicSourceCode"])
        self.assertTrue(any(line.startswith("NO MATCH") for line in report))


@unittest.skipUnless(REAL_TREE.exists() and REAL_CSV.exists(), "real Moshavere files absent")
class RealFilesTest(unittest.TestCase):
    def test_counts_and_determinism(self):
        bundle, _ = conv.build(REAL_TREE, REAL_CSV)
        self.assertEqual(len(bundle["topics"]), 5656)
        self.assertEqual(sum(1 for t in bundle["topics"] if t["parentSourceCode"] is None), 23)
        first = conv.dump(bundle).encode("utf-8")
        bundle2, _ = conv.build(REAL_TREE, REAL_CSV)
        self.assertEqual(first, conv.dump(bundle2).encode("utf-8"))
        self.assertEqual(json.loads(first)["format"], 1)


if __name__ == "__main__":
    unittest.main()
