package com.rsicms.rsuite.utils.search;

import java.util.ArrayList;
import java.util.List;

/**
 * Pair of name and values
 */
public class NameValuesPair {

  private String name;
  private String[] values;

  public NameValuesPair(String name, String... values) {
    this.name = name;
    this.values = values;
  }

  public String getName() {
    return name;
  }

  public String[] getValues() {
    return values;
  }

  /**
   * Create a list of one NameValuePair instance, which more could be added to.
   * <p>
   * This is nothing more than a convenience method.
   * 
   * @param name
   * @param values
   * @return a list of one name-value pair instance.
   */
  public static List<NameValuesPair> getStarterList(String name, String... values) {
    List<NameValuesPair> starterList = new ArrayList<NameValuesPair>();
    starterList.add(new NameValuesPair(name, values));
    return starterList;
  }

}
