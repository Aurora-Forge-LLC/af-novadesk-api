package com.af.novadesk.api.department.constants;

/**
 * Which department list to return: the organization's own set (used when
 * creating a user with no legal entity involved), or a specific entity's set.
 */
public enum DepartmentScope {
    ORG,
    ENTITY
}
