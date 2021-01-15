package top.javatool.canal.client.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.persistence.Column;
import javax.persistence.Transient;
import org.apache.commons.lang3.reflect.FieldUtils;

/**
 * @author yang peng
 * @date 2019/3/2915:07
 */
public class EntryUtil {


  private static Map<Class, Map<String, String>> cache = new ConcurrentHashMap<>();


  /**
   * 获取字段名称和实体属性的对应关系
   *
   * @param c class
   * @return map
   */
  public static Map<String, String> getFieldName(Class c) {
    Map<String, String> map = cache.get(c);
    if (map == null) {
      List<Field> fields = FieldUtils.getAllFieldsList(c);
      //如果实体类中存在column 注解，则使用column注解的名称为字段名
      map = fields.stream().filter(EntryUtil::notTransient)
          .filter(field -> !Modifier.isStatic(field.getModifiers()))
          .collect(Collectors.toMap(EntryUtil::getColumnName, Field::getName));
      cache.putIfAbsent(c, map);
    }
    return map;
  }


  private static String getColumnName(Field field) {
    Column annotation = field.getAnnotation(Column.class);
    if (annotation != null) {
      return annotation.name();
    } else {
      return HumpToUnderline(field.getName());
    }
  }

  public static String HumpToUnderline(String para) {
    StringBuilder sb = new StringBuilder(para);
    int temp = 0;//定位
    if (!para.contains("_")) {
      for (int i = 0; i < para.length(); i++) {
        if (Character.isUpperCase(para.charAt(i))) {
          sb.insert(i + temp, "_");
          temp += 1;
        }
      }
    }
    return sb.toString().toLowerCase();
  }

  private static boolean notTransient(Field field) {
    Transient annotation = field.getAnnotation(Transient.class);
    return annotation == null;
  }


}
