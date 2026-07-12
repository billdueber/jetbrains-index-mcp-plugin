# File Structure — Class with Inheritance

## 1. testClassWithInheritance

**Purpose**: Verify that `ide_file_structure` correctly shows classes with superclass and their methods.

**Setup**: Fixture file `ruby/agent_tests/file_structure/fixtures/class_with_inheritance.rb` already exists.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with `project_path = "/Users/dueberb/devel/ai/jbimcp"`, `file = "ruby/agent_tests/file_structure/fixtures/class_with_inheritance.rb"` | Must succeed |
| 3 | Inspect `response.structure` | Contains `"class Animal (line 1)"` |
| 4 | Inspect `response.structure` | Contains `"class Dog < Animal (line 7)"` |
| 5 | Inspect `response.structure` | Contains `"method speak () (line 2)"` |
| 6 | Inspect `response.structure` | Contains `"method bark () (line 8)"` |
| 7 | Inspect `response.structure` | `"speak"` appears before `"Dog"` in the output (speak is a child of Animal) |

## 2. testSuperclassInSignature

**Purpose**: Verify that the superclass name appears in the class signature.

**Setup**: Same fixture.

| Step | Action | Expected response |
|------|--------|-------------------|
| 1 | `ide_sync_files` with no arguments | `syncedAll: true` |
| 2 | `ide_file_structure` with same parameters | Must succeed |
| 3 | Inspect `response.structure` | Contains `"< Animal"` or `"<Animal"` in the Dog line |

## Summary

| Fixture | Expected structure elements |
|---------|---------------------------|
| `class_with_inheritance.rb` | `class Animal` with `method speak`, `class Dog < Animal` with `method bark` |