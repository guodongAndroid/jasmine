## Jasmine(茉莉)

一个适用于 Mybatis-Plus 的 Java 注解处理器.

### 使用

假设有以下 `User` 类：它被 `@Jasmine` 注解标记，其中的 `aByte` 和 `aChar` 成员变量被 `@Ignore` 注解标记

```java
@Jasmine(enabled = true, sqlEscaping = true, useOriginalVariableName = false)
public class User {

    @TableField("user_name")
    private String name;

    private String parentName;

    private String phoneNumber;

    @Ignore
    private byte aByte;

    @Ignore
    private char aChar;

}
```

编译之后：Jasmine 会为 `User` 类中没有被 `@Ignore` 注解标记的成员变量生成公开常量。

```java
public class User {
    public static final String PHONENUMBER = "`phoneNumber`";
    public static final String PARENTNAME = "`parentName`";
    public static final String USER_NAME = "`user_name`";
    @TableField("user_name")
    private String name;
    private String parentName;
    private String phoneNumber;
    private byte aByte;
    private char aChar;

    public User() {
    }
}
```

### Mybatis-Plus

```java
QueryWrapper<User> wrapper = Wrappers.query(User.class);
wrapper.select(User.USER_NAME, User.PARENTNAME, User.PHONENUMBER);
User user = mapper.selectOne(wrapper);
```



