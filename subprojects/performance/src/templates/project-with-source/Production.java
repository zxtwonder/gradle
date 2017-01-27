package ${packageName};

public class ${productionClassName} ${extendsAndImplementsClause} {
    private final String property;

    public ${productionClassName}() {
        this.property = "foo";
    }

    public String getProperty() {
        return property;
    }
<% propertyCount.times { %>
    private String prop${it};

    public String getProp${it}() {
        return prop${it};
    }

    public void setProp${it}(String value) {
        prop${it} = value;
    }
<% } %>
}
