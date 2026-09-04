package com.decathlon.tzatziki.utils.sql;

public record TruncateSpec(SqlIdentifier table, boolean restartIdentity, boolean cascade) {

    public static Builder table(String table) {
        return new Builder(SqlIdentifier.table(table));
    }

    public static final class Builder {
        private final SqlIdentifier table;
        private boolean restartIdentity = true;
        private boolean cascade = true;

        private Builder(SqlIdentifier table) {
            this.table = table;
        }

        public Builder withoutRestartIdentity() {
            this.restartIdentity = false;
            return this;
        }

        public Builder withoutCascade() {
            this.cascade = false;
            return this;
        }

        public TruncateSpec build() {
            return new TruncateSpec(table, restartIdentity, cascade);
        }
    }
}
