# Java and Python `ide_call_hierarchy` test coverage map

This document intentionally summarizes the kinds of coverage that exist for Java and Python call hierarchy, without documenting implementation mechanics. It is meant as a checklist for comparing Java/Python coverage against the Ruby call hierarchy tests.

## Covered test types

### Tool contract and validation

| Test type | Java coverage | Python coverage | Ruby comparison |
|-----------|---------------|-----------------|-----------------|
| Missing parameters | Covered by shared `ide_call_hierarchy` tool tests. | Covered by shared `ide_call_hierarchy` tool tests. | Covered in Ruby error tests. |
| Invalid file / missing file | Covered by shared `ide_call_hierarchy` tool tests. | Covered by shared `ide_call_hierarchy` tool tests. | Covered in Ruby error tests. |
| Symbol without language | Covered by shared `ide_call_hierarchy` tool tests. | Covered by shared `ide_call_hierarchy` tool tests. | Covered by Ruby symbol lookup test, but Ruby symbol resolution is currently only documented as expected/stubbed. |
| Unsupported language | Covered by shared `ide_call_hierarchy` tool tests. | Covered by shared `ide_call_hierarchy` tool tests. | Covered by Ruby invalid-direction tests and shared schema coverage. |
| Invalid direction | Not separately covered for Java or Python. | Not separately covered for Java or Python. | Covered by Ruby error tests. |
| Missing direction | Not separately covered for Java or Python. | Not separately covered for Java or Python. | Covered by Ruby error tests. |
| Position outside method | Not separately covered for Java or Python. | Not separately covered for Java or Python. | Covered by Ruby error tests. |
| Empty file | Not separately covered for Java or Python. | Not separately covered for Java or Python. | Covered by Ruby error tests. |
| Result schema | Covered by shared tool unit tests. | Covered by shared tool unit tests. | Covered by shared tool contract expectations. |
| Result serialization | Covered by shared model tests, including nested call children. | Covered by shared model tests, including nested call children. | Covered by shared result shape expectations. |

### Direction coverage

| Test type | Java coverage | Python coverage | Ruby comparison |
|-----------|---------------|-----------------|-----------------|
| Caller direction | Covered by Java integration tests and Python handler tests. | Covered by Python handler tests. | Covered by Ruby simple callers and cross-file callers tests. |
| Callee direction | Not covered by Java-specific tests. | Not covered by Python-specific tests. | Covered by Ruby simple callees and cross-file callees tests. |
| Caller and callee symmetry | Not covered together for Java or Python. | Not covered together for Java or Python. | Covered by Ruby simple callers/simple callees test pairs. |
| Recursive callee == caller | Not covered by Java or Python tests. | Not covered by Java or Python tests. | Covered by Ruby recursive method test. |
| No callers | Not covered by Java or Python tests. | Not covered by Java or Python tests. | Covered by Ruby no-caller test. |
| No callees | Not covered by Java or Python tests. | Not covered by Java or Python tests. | Covered by Ruby no-callee test. |

### Depth and traversal coverage

| Test type | Java coverage | Python coverage | Ruby comparison |
|-----------|---------------|-----------------|-----------------|
| Immediate depth = 1 results | Covered indirectly by Java/Python caller tests. | Covered by Python caller test. | Covered by Ruby depth-1 caller/callee tests. |
| Transitive callers | Not covered by Java or Python tests. | Not covered by Java or Python tests. | Covered by Ruby callers-with-depth and deep-callers tests. |
| Transitive callees | Not covered by Java or Python tests. | Not covered by Java or Python tests. | Covered by Ruby callees-with-depth and deep-callees tests. |
| Depth limit at one level | Not separately covered by Java or Python tests. | Not separately covered by Java or Python tests. | Covered by Ruby depth-limit tests. |
| Cycle guard / no infinite recursion | Not covered by Java or Python tests. | Not covered by Java or Python tests. | Covered by Ruby recursive method test. |

### Relationship shape coverage

| Test type | Java coverage | Python coverage | Ruby comparison |
|-----------|---------------|-----------------|-----------------|
| One target called by multiple callers | Covered by Java scope tests and Python caller tests. | Covered by Python caller test. | Covered by Ruby simple callers test. |
| One caller calling multiple callees | Not covered by Java or Python tests. | Not covered by Java or Python tests. | Covered by Ruby simple callees test. |
| Caller in a different file | Covered by Java project-production and library/project scope tests. | Not covered by Python tests. | Covered by Ruby cross-file callers and cross-file callees tests. |
| Library/source-root relationship | Covered by Java `project_and_libraries` scope test. | Not covered by Python tests. | Not separately covered by Ruby tests. |
| Test source vs production source | Covered by Java `project_production_files` scope test. | Not covered by Python tests. | Not separately covered by Ruby tests. |
| Class methods | Not covered by Java or Python call hierarchy tests. | Not covered by Java or Python call hierarchy tests. | Covered by Ruby class-method caller tests. |
| Instance methods | Not covered by Java or Python call hierarchy tests. | Not covered by Java or Python call hierarchy tests. | Covered by Ruby instance-method caller/callee tests. |
| Mixed instance/class relationships | Not covered by Java or Python call hierarchy tests. | Not covered by Java or Python call hierarchy tests. | Covered by Ruby mixed-instance/class chain test. |
| Predicate method names (`?`) | Not covered by Java or Python tests. | Not covered by Java or Python tests. | Covered by Ruby predicate test. |
| Bang method names (`!`) | Not covered by Java or Python tests. | Not covered by Java or Python tests. | Covered by Ruby bang test. |
| Qualified method names (`Class#method`) | Not covered by Java or Python call hierarchy tests. | Not covered by Java or Python call hierarchy tests. | Covered by Ruby class/instance method naming expectations. |

### Scope and filtering coverage

| Test type | Java coverage | Python coverage | Ruby comparison |
|-----------|---------------|-----------------|-----------------|
| `project_production_files` scope | Covered for Java callers. | Not covered. | Not separately covered by Ruby tests. |
| `project_and_libraries` scope | Covered for Java callers. | Not covered. | Not separately covered by Ruby tests. |
| `project_files` scope with libraries excluded | Not covered by Java call hierarchy tests. | Not covered. | Not separately covered by Ruby tests. |
| Test-source exclusion | Covered indirectly by Java production-scope test. | Not covered. | Not separately covered by Ruby tests. |
| Language-specific scope filtering | Covered for Java in shared/navigation tests. | Not covered by Python call hierarchy tests. | Not separately covered by Ruby tests. |

## Summary of gaps versus Ruby tests

Java/Python currently do not provide Ruby-equivalent coverage for:

- Callee direction.
- Caller and callee test pairs.
- Recursive methods where callee equals caller.
- Transitive caller chains.
- Transitive callee chains.
- Explicit depth-limit checks beyond depth = 1 behavior.
- No-caller and no-callee edge cases.
- Cross-file relationships in Python.
- Class methods.
- Instance methods.
- Mixed instance/class method relationships.
- Predicate method names such as `valid?`.
- Bang method names such as `save!`.
- Qualified method naming such as `Class#method` or `Class.method`.
- Invalid direction and missing direction errors.
- Position outside any method.
- Empty file errors.
- Symbol-based lookup behavior for Java and Python.
- Library exclusion behavior with `project_files` or `project_production_files`.
- Python plugin-unavailable or native Python API-missing behavior.
