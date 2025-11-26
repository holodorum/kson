use super::*;

#[derive(Default)]
struct TestResults {
    kson_pass: usize,
    kson_fail: usize,
    json_pass: usize,
    json_fail: usize,
    yaml_pass: usize,
    yaml_fail: usize,
    no_pass: usize,
    no_fail: usize,
}

fn print_diff(test_name: &str, format: &str, expected: &str, actual: &str) {
    eprintln!("\n=== FAIL: {} {} output mismatch ===", test_name, format);
    eprintln!("Expected:\n{}", expected.trim());
    eprintln!("\nActual:\n{}", actual.trim());
    eprintln!("=====================================\n");
}

fn test_format_output(
    test_name: &str,
    format: &str,
    expected: &str,
    actual: &str,
) -> bool {
    if expected.trim() == actual.trim() {
        true
    } else {
        print_diff(test_name, format, expected, actual);
        false
    }
}

fn check_kson_format(
    results: &mut TestResults,
    test_name: &str,
    input: &str,
    expected_path: &std::path::Path,
) {
    if !expected_path.exists() {
        eprintln!("FAIL: {} KSON expected file missing", test_name);
        return;
    }

    let expected = std::fs::read_to_string(expected_path).unwrap();
    let indent = IndentType::Spaces(indent_type::Spaces::new(2));
    let actual = Kson::format(input, FormatOptions::new(indent, FormattingStyle::Plain));

    if test_format_output(test_name, "KSON", &expected, &actual) {
        results.kson_pass += 1;
    } else {
        results.kson_fail += 1;
    }
}

fn test_json_conversion(
    results: &mut TestResults,
    test_name: &str,
    input: &str,
    expected_path: &std::path::Path,
) {
    if !expected_path.exists() {
        eprintln!("FAIL: {} JSON expected file missing", test_name);
        return;
    }

    let expected = std::fs::read_to_string(expected_path).unwrap();
    match Kson::to_json(input, false) {
        Ok(success) => {
            let actual = success.output();
            if test_format_output(test_name, "JSON", &expected, &actual) {
                results.json_pass += 1;
            } else {
                results.json_fail += 1;
            }
        }
        Err(err) => {
            results.json_fail += 1;
            eprintln!("FAIL: {} should parse but got error: {:#?}", test_name, err);
        }
    }
}

fn test_yaml_conversion(
    results: &mut TestResults,
    test_name: &str,
    input: &str,
    expected_path: &std::path::Path,
) {
    if !expected_path.exists() {
        eprintln!("FAIL: {} YAML expected file missing", test_name);
        return;
    }

    let expected = std::fs::read_to_string(expected_path).unwrap();
    match Kson::to_yaml(input, false) {
        Ok(success) => {
            let actual = success.output();
            if test_format_output(test_name, "YAML", &expected, &actual) {
                results.yaml_pass += 1;
            } else {
                results.yaml_fail += 1;
            }
        }
        Err(err) => {
            results.yaml_fail += 1;
            eprintln!("FAIL: {} should parse but got error: {:#?}", test_name, err);
        }
    }
}

fn test_negative_case(results: &mut TestResults, test_name: &str, input: &str) {
    match Kson::to_json(input, true) {
        Err(_) => {
            results.no_pass += 1;
        }
        Ok(success) => {
            results.no_fail += 1;
            eprintln!("\n=== FAIL: {} should fail but parsed successfully ===", test_name);
            eprintln!("Input:\n{}", input.trim());
            eprintln!("\nParsed output:\n{}", success.output().trim());
            eprintln!("================================================\n");
        }
    }
}

#[test]
fn test_ksonsuite() {
    let suite_dir = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .unwrap()
        .parent()
        .unwrap()
        .join("build/ksonsuite");

    if !suite_dir.exists() {
        panic!("Test suite directory not found: {:?}", suite_dir);
    }

    let mut results = TestResults::default();

    for entry in std::fs::read_dir(&suite_dir).unwrap() {
        let entry = entry.unwrap();
        let path = entry.path();
        let name = path.file_name().unwrap().to_str().unwrap();

        if name.starts_with("y_") && name.ends_with(".input.kson") {
            let hash_part = name.replace(".input.kson", "");
            let input = std::fs::read_to_string(&path).unwrap();

            let expected_kson_path = suite_dir.join(format!("{}.expected.kson", hash_part));
            check_kson_format(&mut results, name, &input, &expected_kson_path);

            let expected_json_path = suite_dir.join(format!("{}.expected.json", hash_part));
            test_json_conversion(&mut results, name, &input, &expected_json_path);

            let expected_yaml_path = suite_dir.join(format!("{}.expected.yaml", hash_part));
            test_yaml_conversion(&mut results, name, &input, &expected_yaml_path);
        } else if name.starts_with("n_") && name.ends_with(".input.kson") {
            let input = std::fs::read_to_string(&path).unwrap();
            test_negative_case(&mut results, name, &input);
        }
    }

    println!("Yes KSON tests: {} passed, {} failed", results.kson_pass, results.kson_fail);
    println!("Yes JSON tests: {} passed, {} failed", results.json_pass, results.json_fail);
    println!("Yes YAML tests: {} passed, {} failed", results.yaml_pass, results.yaml_fail);
    println!("No tests: {} passed, {} failed", results.no_pass, results.no_fail);

    assert_eq!(results.kson_fail, 0, "Some 'yes' KSON tests failed");
    assert_eq!(results.json_fail, 0, "Some 'yes' JSON tests failed");
    assert_eq!(results.yaml_fail, 0, "Some 'yes' YAML tests failed");
    assert_eq!(results.no_fail, 0, "Some 'no' tests parsed but should have failed");
}