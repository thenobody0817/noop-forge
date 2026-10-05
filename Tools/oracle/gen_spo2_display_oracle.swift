import Foundation

// Tools/oracle/gen_spo2_display_oracle.swift — regenerates spo2_display_oracle.json.
//
// Compiled standalone against the real Packages/StrandAnalytics/Sources/StrandAnalytics/Spo2Display.swift
// and driven over an exhaustive grid of the four inputs, so the fixture is the Swift build's own answer
// rather than a re-derivation of it. Run by Tools/oracle/gen_spo2_display_oracle.sh on any machine with a
// Swift toolchain; the result is committed and read by BOTH the Kotlin and the Swift test, so neither
// platform can drift without the other failing.
//
// The grid is named rather than literal because JSON has no NaN or Infinity literal. Both platforms map
// the same names to the same doubles, so the names are the shared contract for the inputs.

/// Grid values, in a fixed order. "nil" is Swift's absent Double?.
let NAMES = ["nil", "0", "neg", "90", "96", "97", "nan", "inf", "neginf"]

func value(_ name: String) -> Double? {
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
    default: fatalError("unknown grid name \(name)")
    }
}

let provenanceName: (Spo2Display.Provenance) -> String = {
    switch $0 {
    case .measured: return "measured"
    case .measuredCarried: return "measuredCarried"
    case .candidate: return "candidate"
    }
}

var cases: [String] = []
var matched = 0
var nilCount = 0

for t in NAMES {
    for c in NAMES {
        for k in NAMES {
            for e in [true, false] {
                let r = Spo2Display.resolve(
                    todayPct: value(t), candidatePct: value(c),
                    candidateEnabled: e, carriedPct: value(k))
                if let r = r {
                    matched += 1
                    // %.2f renders a nan/inf that slipped through as nan/inf, which is still a parseable
                    // token; it cannot happen, because resolve rejects every non-finite input.
                    cases.append(
                        "    {\"t\": \"\(t)\", \"c\": \"\(c)\", \"k\": \"\(k)\", \"e\": \(e), "
                        + "\"percent\": \(String(format: "%.2f", r.percent)), "
                        + "\"provenance\": \"\(provenanceName(r.provenance))\"}")
                } else {
                    nilCount += 1
                    cases.append(
                        "    {\"t\": \"\(t)\", \"c\": \"\(c)\", \"k\": \"\(k)\", \"e\": \(e), "
                        + "\"percent\": null, \"provenance\": null}")
                }
            }
        }
    }
}

let gridList = NAMES.map { "\"" + $0 + "\"" }.joined(separator: ", ")

let NOTE = "Produced by compiling Packages/StrandAnalytics/Sources/StrandAnalytics/Spo2Display.swift "
    + "standalone and calling Spo2Display.resolve over the full cross product of the named grid below. "
    + "Inputs are named rather than literal because JSON has no NaN or Infinity; both platforms map the "
    + "same names to the same doubles. Read by Spo2DisplayParityOracleTest.kt and Spo2DisplayTests.swift "
    + "from the same committed file."

var out: [String] = []
out.append("{")
out.append("  \"note\": \"" + NOTE + "\",")
out.append("  \"producedBy\": {")
out.append("    \"toolchain\": \"Swift " + swiftVersionString + "\",")
out.append("    \"sourceCommit\": \"" + sourceCommit + "\",")
out.append("    \"caseCount\": " + String(cases.count) + ",")
out.append("    \"matched\": " + String(matched) + ",")
out.append("    \"nil\": " + String(nilCount))
out.append("  },")
out.append("  \"grid\": {")
out.append("    \"values\": [" + gridList + "],")
out.append("    \"enabled\": [true, false]")
out.append("  },")
out.append("  \"cases\": [")
out.append(cases.joined(separator: ",\n"))
out.append("  ]")
out.append("}")
out.append("")

print(out.joined(separator: "\n"), terminator: "")
FileHandle.standardError.write(
    "cases=\(cases.count) matched=\(matched) nil=\(nilCount)\n".data(using: .utf8)!)
