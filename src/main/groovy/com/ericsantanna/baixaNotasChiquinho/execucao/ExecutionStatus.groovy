package com.ericsantanna.baixaNotasChiquinho.execucao

class ExecutionStatus {
    String franqueadora
    String status
    String errorMessage

    ExecutionStatus(String franqueadora, String status) {
        this.franqueadora = franqueadora
        this.status = status
    }

    ExecutionStatus(String franqueadora, String status, String errorMessage) {
        this.franqueadora = franqueadora
        this.status = status
        this.errorMessage = errorMessage
    }

    boolean equals(Object other) {
        if (this.is(other)) return true
        if (!(other instanceof ExecutionStatus)) return false
        return this.franqueadora == other.franqueadora
    }

    int hashCode() {
        return franqueadora?.hashCode() ?: 0
    }
}
