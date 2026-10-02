import XCTest
@testable import StrandAnalytics

/// BYTE-IDENTICAL PARITY ORACLE for `Spo2Display.resolve`, read from the same committed fixture as the
/// Kotlin `Spo2DisplayParityOracleTest`.
///
/// `android/app/src/test/resources/spo2_display_oracle.json` is this engine's own answer over the full
/// cross product of its four inputs, produced by `Tools/oracle/gen_spo2_display_oracle.sh` compiling this
/// file standalone. There is ONE copy of the fixture, in the Android test tree, and this suite walks up to
/// it from its own location - the same way `LocalDayWindowsTests` reaches its oracle. The Kotlin suite
/// reads that same file as a classpath resource. Because the two engines are independent
/// reimplementations of the same three-way rule, resolving the same inputs against one shared answer is
/// what catches a one-sided edit.
///
/// NAMED INPUTS. JSON has no NaN or Infinity literal, so the grid names its values and both platforms map
/// the same names to the same doubles. Changing a mapping here without changing the Kotlin one turns 1,458
/// passing cases into 1,458 failures, which is the intended direction to break in.
final class Spo2DisplayParityOracleTests: XCTestCase {

    /// The committed oracle, loaded by a path derived from this file's own location.
    ///
    /// It fails rather than skips when the file is absent: an oracle nobody can find would otherwise let
    /// both platforms stay green while they disagree, which is the whole thing this file guards.
    private func loadOracle() throws -> [String: Any] {
        let relative = "android/app/src/test/resources/spo2_display_oracle.json"
        var dir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        for _ in 0..<8 {
            let candidate = dir.appendingPathComponent(relative)
            if FileManager.default.fileExists(atPath: candidate.path) {
                let data = try Data(contentsOf: candidate)
                let parsed = try JSONSerialization.jsonObject(with: data)
                return try XCTUnwrap(parsed as? [String: Any], "the oracle must be a JSON object")
            }
            dir = dir.deletingLastPathComponent()
        }
        XCTFail("committed oracle \(relative) not found above \(#filePath) — this test must not pass by default")
        throw CocoaError(.fileNoSuchFile)
    }

    private func gridValue(_ name: String) -> Double? {
        switch name {
        case "nil": return nil
        case "0": return 0.0
        case "neg": return -1.0
        case "90": return 90.0
        case "96": return 96.0
        case "97": return 97.0
        case "nan": return .nan
        case "inf": return .infinity
        case "neginf": return -.infinity
        default: XCTFail("oracle names an input the grid does not define: \(name)"); return nil
        }
    }

    private func provenance(_ name: String) -> Spo2Display.Provenance {
        switch name {
        case "measured": return .measured
        case "measuredCarried": return .measuredCarried
        case "candidate": return .candidate
        default: XCTFail("oracle names a provenance this platform does not define: \(name)"); return .measured
        }
    }

    func testEveryCaseMatchesTheCompiledSwiftTwin() throws {
        let root = try loadOracle()
        let cases = try XCTUnwrap(root["cases"] as? [[String: Any]])
        XCTAssertFalse(cases.isEmpty, "the fixture must not be empty")

        var seen = Set<String>()
        for c in cases {
            let t = c["t"] as! String
            let cand = c["c"] as! String
            let k = c["k"] as! String
            let enabled = c["e"] as! Bool
            let label = "t=\(t) c=\(cand) k=\(k) e=\(enabled)"

            let out = Spo2Display.resolve(todayPct: gridValue(t), candidatePct: gridValue(cand),
                                          candidateEnabled: enabled, carriedPct: gridValue(k))

            guard let expectedProvenance = c["provenance"] as? String else {
                XCTAssertNil(out, "\(label) should not resolve")
                continue
            }
            let resolved = try XCTUnwrap(out, "\(label) should have resolved to \(expectedProvenance)")
            XCTAssertEqual(provenance(expectedProvenance), resolved.provenance, label)
            let expectedPercent = try XCTUnwrap(c["percent"] as? Double)
            XCTAssertEqual(expectedPercent, resolved.percent, accuracy: 1e-9, label)
            seen.insert(expectedProvenance)
        }

        XCTAssertEqual(["measured", "measuredCarried", "candidate"], seen.sorted(),
                       "every provenance value must be pinned")
    }

    /// The self-defence guard: a fixture that silently loses cases still passes the loop above, so assert
    /// the cross product is whole.
    func testTheFixtureStillCoversTheWholeCrossProduct() throws {
        let root = try loadOracle()
        let grid = try XCTUnwrap(root["grid"] as? [String: Any])
        let n = try XCTUnwrap(grid["values"] as? [String]).count
        let e = try XCTUnwrap(grid["enabled"] as? [Bool]).count
        let cases = try XCTUnwrap(root["cases"] as? [[String: Any]])
        XCTAssertEqual(n * n * n * e, cases.count,
                       "case count must be the full cross product of the grid")
    }
}