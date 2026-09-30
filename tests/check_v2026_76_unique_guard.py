"""Read-only structural regression check. Run: python tests/check_v2026_76_unique_guard.py"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
FLYWAY = ROOT / "zw-insight-server/zw-app/src/main/resources/db/migration/V2026_76__logical_delete_unique_guard.sql"
INITDB = ROOT / "deploy/db-init/78_V2026_76__logical_delete_unique_guard.sql"


def check(sql):
    code = re.sub(r"--[^\n]*", "", sql)
    blocks = re.findall(
        r"SET @sql = IF\(@index_columns IS NULL, 'ALTER TABLE `([^`]+)` ADD UNIQUE KEY `([^`]+)` (\([^'\n]+\))', IF\(@index_columns <> '([^']+)' OR @index_non_unique <> 0, 'ALTER TABLE `\1` DROP INDEX `\2`, ADD UNIQUE KEY `\2` \3', 'SELECT 1'\)\);\nPREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;",
        code,
    )
    assert len(blocks) == 27, "Each index needs one prepared missing/add, old/replace, target/no-op statement"
    assert len({(table, index) for table, index, *_ in blocks}) == 27
    assert len({table for table, *_ in blocks}) == 26
    alters = re.findall(r"'ALTER TABLE [^']+'", code)
    drops = [alter for alter in alters if "DROP INDEX" in alter]
    assert len(drops) == 27
    assert all(
        re.fullmatch(r"'ALTER TABLE `[^`]+` DROP INDEX `([^`]+)`, ADD UNIQUE KEY `\1` \([^']+\)'", alter)
        for alter in drops
    ), "No separate drop-only ALTER is permitted"
    assert "biz_cost_account_txn" not in code and "uk_txn_source" not in code, "Ledger must remain untouched"
    assert code.count("GENERATED ALWAYS AS (IF(`deleted` = 0, 0, NULL)) STORED") == 27
    for _, _, definition, columns in blocks:
        names = re.findall(r"`([^`]+)`", definition)
        assert ",".join(names) == columns
        assert names[-1] == "unique_active_guard"


if __name__ == "__main__":
    assert FLYWAY.read_bytes() == INITDB.read_bytes(), "Migration twins must be byte-identical"
    sql = FLYWAY.read_text(encoding="utf-8")
    check(sql)
    # ponytail: structural safety only; verify execution/rollback on isolated MySQL before release.
    broken = sql.replace("DROP INDEX `uk_username`, ADD UNIQUE KEY `uk_username` (`username`, `unique_active_guard`)", "DROP INDEX `uk_username`", 1)
    try:
        check(broken)
    except AssertionError:
        pass
    else:
        raise AssertionError("Regression check accepted a drop-only ALTER")
    print("PASS: 26 tables / 27 indexes; atomic replacement, idempotent branches, NULL guard, equal twins, ledger untouched")
