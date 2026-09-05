package com.moodbuds.admin;

import java.util.Set;

record ResourceDefinition(String apiName, String table, Set<String> fields, String displayField,
                          boolean slugged, boolean softDelete, boolean createdBy) {}
