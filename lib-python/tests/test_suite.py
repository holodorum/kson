from pathlib import Path
from kson import Kson, Result, FormatOptions, IndentType, FormattingStyle

SUITE_DIR = Path(__file__).parent.parent.parent / "build" / "ksonsuite"


class TestResults:
    def __init__(self):
        self.kson_pass = 0
        self.kson_fail = 0
        self.json_pass = 0
        self.json_fail = 0
        self.yaml_pass = 0
        self.yaml_fail = 0
        self.no_pass = 0
        self.no_fail = 0


def print_diff(test_name: str, format_type: str, expected: str, actual: str):
    print(f"\n=== FAIL: {test_name} {format_type} output mismatch ===", flush=True)
    print(f"Expected:\n{expected.strip()}", flush=True)
    print(f"\nActual:\n{actual.strip()}", flush=True)
    print("=" * 53 + "\n", flush=True)


def test_format_output(test_name: str, format_type: str, expected: str, actual: str) -> bool:
    if expected.strip() == actual.strip():
        return True
    else:
        print_diff(test_name, format_type, expected, actual)
        return False


def check_kson_format(results: TestResults, test_name: str, input_kson: str, expected_path: Path):
    if not expected_path.exists():
        print(f"FAIL: {test_name} KSON expected file missing", flush=True)
        return

    expected = expected_path.read_text()
    actual = Kson.format(input_kson, FormatOptions(IndentType.Spaces(2), FormattingStyle.PLAIN))

    if test_format_output(test_name, "KSON", expected, actual):
        results.kson_pass += 1
    else:
        results.kson_fail += 1


def test_json_conversion(results: TestResults, test_name: str, input_kson: str, expected_path: Path):
    if not expected_path.exists():
        print(f"FAIL: {test_name} JSON expected file missing", flush=True)
        return

    expected = expected_path.read_text()
    result = Kson.to_json(input_kson, False)

    if isinstance(result, Result.Success):
        actual = result.output()
        if test_format_output(test_name, "JSON", expected, actual):
            results.json_pass += 1
        else:
            results.json_fail += 1
    else:
        results.json_fail += 1
        print(f"FAIL: {test_name} should parse but got error: {result}", flush=True)


def test_yaml_conversion(results: TestResults, test_name: str, input_kson: str, expected_path: Path):
    if not expected_path.exists():
        print(f"FAIL: {test_name} YAML expected file missing", flush=True)
        return

    expected = expected_path.read_text()
    result = Kson.to_yaml(input_kson, False)

    if isinstance(result, Result.Success):
        actual = result.output()
        if test_format_output(test_name, "YAML", expected, actual):
            results.yaml_pass += 1
        else:
            results.yaml_fail += 1
    else:
        results.yaml_fail += 1
        print(f"FAIL: {test_name} should parse but got error: {result}", flush=True)


def test_negative_case(results: TestResults, test_name: str, input_kson: str):
    result = Kson.to_json(input_kson, True)

    if isinstance(result, Result.Failure):
        results.no_pass += 1
    else:
        results.no_fail += 1
        print(f"\n=== FAIL: {test_name} should fail but parsed successfully ===", flush=True)
        print(f"Input:\n{input_kson.strip()}", flush=True)
        print(f"\nParsed output:\n{result.output().strip()}", flush=True)
        print("=" * 53 + "\n", flush=True)


def test_ksonsuite():
    if not SUITE_DIR.exists():
        raise Exception(f"Test suite directory not found: {SUITE_DIR}")

    results = TestResults()

    for entry in sorted(SUITE_DIR.iterdir()):
        name = entry.name

        if name.startswith("y_") and name.endswith(".input.kson"):
            hash_part = name.replace(".input.kson", "")
            input_kson = entry.read_text()

            expected_kson_path = SUITE_DIR / f"{hash_part}.expected.kson"
            check_kson_format(results, name, input_kson, expected_kson_path)

            expected_json_path = SUITE_DIR / f"{hash_part}.expected.json"
            test_json_conversion(results, name, input_kson, expected_json_path)

            expected_yaml_path = SUITE_DIR / f"{hash_part}.expected.yaml"
            test_yaml_conversion(results, name, input_kson, expected_yaml_path)

        elif name.startswith("n_") and name.endswith(".input.kson"):
            input_kson = entry.read_text()
            test_negative_case(results, name, input_kson)

    print(f"Yes KSON tests: {results.kson_pass} passed, {results.kson_fail} failed")
    print(f"Yes JSON tests: {results.json_pass} passed, {results.json_fail} failed")
    print(f"Yes YAML tests: {results.yaml_pass} passed, {results.yaml_fail} failed")
    print(f"No tests: {results.no_pass} passed, {results.no_fail} failed")

    assert results.kson_fail == 0, "Some 'yes' KSON tests failed"
    assert results.json_fail == 0, "Some 'yes' JSON tests failed"
    assert results.yaml_fail == 0, "Some 'yes' YAML tests failed"
    assert results.no_fail == 0, "Some 'no' tests parsed but should have failed"