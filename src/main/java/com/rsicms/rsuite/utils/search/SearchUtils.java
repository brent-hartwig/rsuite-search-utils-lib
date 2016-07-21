package com.rsicms.rsuite.utils.search;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.content.ContentDisplayObject;
import com.reallysi.rsuite.api.search.RSuiteQueryType;
import com.reallysi.rsuite.api.search.Search;
import com.reallysi.rsuite.api.search.SortOrder;
import com.reallysi.rsuite.service.SearchService;

/**
 * A collection of static search-related utility methods serving as a wrapper to RXS, in order to
 * construct performant XPath expressions.
 * <p>
 * In July 2016, started adding instance methods that wrap static methods. These are present to
 * facilitate unit testing. Those methods have names matching that of their static counterparts, but
 * begin with a lower case "i" for "instance".
 * <p>
 * This class should be extended as necessary.
 * <p>
 * IDEA, 20150811: Consider replacing single quotes in search values with double quotes. It's a
 * means to support single quotes in search values, as needed by another project. See
 * https://rsicms.atlassian.net/wiki/display/RE/2015/08/10/XQuery+parameters+ with+single+quotes.
 */
public class SearchUtils {

  /**
   * Class log
   */
  private final static Log log = LogFactory.getLog(SearchUtils.class);

  /**
   * XPath expression for any CA. Excludes CANodes.
   */
  public final static String XPATH_ANY_CA = "/rs_ca_map/rs_ca";

  /**
   * XPath expression for any element. Excludes descendants.
   */
  public final static String XPATH_ANY_ELEMENT = "/element()";

  public final static QName QNAME_NON_XML_MO =
      new QName("http://www.rsuitecms.com/rsuite/ns/metadata", "nonxml", "r");

  /**
   * XPath expression for any non-XML MO.
   */
  public final static String XPATH_ANY_NON_XML_MO =
      "/".concat(QNAME_NON_XML_MO.getPrefix()).concat(":").concat(QNAME_NON_XML_MO.getLocalPart());

  /**
   * Materialized view markup for system metadata, leading up to the metadata name
   */
  protected final static String MV_SMD_LEAD_OFF_EXPRESSION = "./mv:metadata/mv:system/mv:";

  /**
   * Materialized view markup for layered metadata, leading up to the metadata name.
   */
  protected final static String MV_LMD_LEAD_OFF_EXPRESSION = "./mv:metadata/mv-lmd:layered/mv-lmd:";

  /**
   * RSuite system metadata info. Add to this as more is accessed.
   */
  public enum SystemMetadata {
    CAType("ca-type"), DateCreated("date-created"), DateModified("last-modified"), DisplayName(
        "display-name"), Id("id"), MimeType("mime-type"), User("user");

    private String localname;

    private SystemMetadata(String localname) {
      this.localname = localname;
    }

    public String getLocalname() {
      return localname;
    }
  }

  /**
   * Get a predicate for a single piece of system metadata, testing equality
   * 
   * @param systemMetadata
   * @param value
   * @see #getSystemMetadataXPathPredicate(SystemMetadata, String, String) for other comparisions.
   * @return A system metadata XPath predicate
   */
  public static String getSystemMetadataXPathPredicate(SystemMetadata systemMetadata,
      String value) {
    return getSystemMetadataXPathPredicate(systemMetadata, "=", value);
  }

  /**
   * Get a predicate for a single piece of system metadata.
   * 
   * @param systemMetadata
   * @param op
   * @param value
   * @return A system metadata XPath predicate
   */
  public static String getSystemMetadataXPathPredicate(SystemMetadata systemMetadata, String op,
      String value) {
    return new StringBuilder("[").append(getMetadataConstraint(MV_SMD_LEAD_OFF_EXPRESSION,
        systemMetadata.getLocalname(), op, new String[] {value})).append("]").toString();
  }

  /**
   * Get a predicate for a single piece of system metadata.
   * <p>
   * Tests equality.
   * 
   * @param name
   * @param values
   * @return A layered metadata XPath predicate
   */
  public static String getLayeredMetadataXPathPredicate(String name, String... values) {
    return new StringBuilder("[").append(getLayeredMetadataConstraint(name, values)).append("]")
        .toString();
  }

  /**
   * Get a layered metadata constraint
   * <p>
   * Caller responsible for placing this in a predicate (brackets).
   * 
   * @param name
   * @param values One or more values to test equality of.
   * @return A layered metadata XPath constraint
   */
  public static String getLayeredMetadataConstraint(String name, String... values) {

    return getMetadataConstraint(MV_LMD_LEAD_OFF_EXPRESSION, name, "=", values);
  }

  /**
   * Get a metadata constraint
   * 
   * @param leadOffExpression
   * @param name
   * @param op
   * @param values One or more values to compare.
   * @return A metadata XPath constraint
   */
  public static String getMetadataConstraint(String leadOffExpression, String name, String op,
      String... values) {
    StringBuilder buf = new StringBuilder(leadOffExpression).append(name).append("/text() ")
        .append(op).append(" (");

    boolean first = true;
    for (String value : values) {
      if (first)
        first = false;
      else
        buf.append(", ");
      buf.append("'").append(value.trim()).append("'");
    }

    return buf.append(")").toString();
  }

  /**
   * Get a word query XPath predicate for a piece of layered metadata.
   * <p>
   * Elected a rudimentary means to enable the search to be case-insensitive. There are several
   * other options to cts:word-query().
   * <p>
   * There may be a need to optimize this, once we have enough content. Optimizations may require
   * index and other database setting changes.
   * 
   * @param name
   * @param value
   * @param caseInsensitive When true, the search is case-insensitive. Else, the default behavior
   *        applies.
   * @return A layered metadata word query predicate
   */
  public static String getLayeredMetadataWordQueryXPathPredicate(String name, String value,
      boolean caseInsensitive) {

    StringBuilder options = new StringBuilder("(");
    if (caseInsensitive)
      options.append("'case-insensitive'");
    options.append(")");

    return new StringBuilder("[cts:contains(").append(MV_LMD_LEAD_OFF_EXPRESSION).append(name)
        .append(", cts:word-query('").append(value).append("', ").append(options.toString())
        .append("))]").toString();

  }

  /**
   * Get an XPath expression for the given qualified name.
   * 
   * @param qname The qualified name of the objects to find.
   * @param allowDescendants Submit true if qualifying objects may not be top-level MOs (slower
   *        search). Submit false if qualifying objects may only be top-level MOs (faster search).
   * @return An XPath expression that selects elements with the specified qualified name. Other
   *         XPath predicates may be added to this XPath expression in order to further restrict the
   *         node set.
   */
  public static String getXPathExpression(QName qname, boolean allowDescendants) {
    StringBuilder query = new StringBuilder();
    if (allowDescendants)
      query.append("/");
    query.append("/");
    boolean haveNamespaceURI = StringUtils.isNotBlank(qname.getNamespaceURI());
    if (haveNamespaceURI)
      query.append("*:");
    query.append(qname.getLocalPart());
    if (haveNamespaceURI)
      query.append("[namespace-uri() = '").append(qname.getNamespaceURI()).append("']");
    return query.toString();
  }

  /**
   * Search for XML MOs, non-XML MOs, or even CANodes.
   * <p>
   * Given a list of MOs is returned, as opposed to an instance of <code>Search</code>, this is only
   * intended to be used when a small number of matches are expected. Alternatives exist, but may
   * not be implemented in this class.
   * 
   * @param user
   * @param searchService
   * @param qname The qualified name of the objects to find.
   * @param allowDescendants Submit true if qualifying objects may not be top-level MOs (slower
   *        search). Submit false if qualifying objects may only be top-level MOs (faster search).
   * @param lmdCriterion Optional LMD name-values pair to incorporate into the search criteria.
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return list of qualifying MOs.
   * @throws RSuiteException
   */
  public static List<ManagedObject> searchForManagedObjects(User user, SearchService searchService,
      QName qname, boolean allowDescendants, NameValuesPair lmdCriterion, int maxResultCount)
      throws RSuiteException {

    List<NameValuesPair> lmdCriteria = new ArrayList<NameValuesPair>(1);
    if (lmdCriterion != null) {
      lmdCriteria.add(lmdCriterion);
    }

    return searchForManagedObjects(user, searchService, qname, allowDescendants, lmdCriteria,
        maxResultCount);
  }

  /**
   * Instance method to search for XML MOs, non-XML MOs, or even CANodes.
   * <p>
   * Simply wraps {@link #searchForManagedObjects(User, SearchService, QName, boolean, List, int)},
   * facilitating unit testing.
   * 
   * @param user
   * @param searchService
   * @param qname The qualified name of the objects to find.
   * @param allowDescendants Submit true if qualifying objects may not be top-level MOs (slower
   *        search). Submit false if qualifying objects may only be top-level MOs (faster search).
   * @param lmdCriteria Optional LMD name-values pairs to incorporate into the search criteria.
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return list of qualifying MOs.
   * @throws RSuiteException
   */
  public List<ManagedObject> iSearchForManagedObjects(User user, SearchService searchService,
      QName qname, boolean allowDescendants, List<NameValuesPair> lmdCriteria, int maxResultCount)
      throws RSuiteException {
    return searchForManagedObjects(user, searchService, qname, allowDescendants, lmdCriteria,
        maxResultCount);
  }

  /**
   * Search for XML MOs, non-XML MOs, or even CANodes.
   * <p>
   * Given a list of MOs is returned, as opposed to an instance of <code>Search</code>, this is only
   * intended to be used when a small number of matches are expected. Alternatives exist, but may
   * not be implemented in this class.
   * 
   * @param user
   * @param searchService
   * @param qname The qualified name of the objects to find.
   * @param allowDescendants Submit true if qualifying objects may not be top-level MOs (slower
   *        search). Submit false if qualifying objects may only be top-level MOs (faster search).
   * @param lmdCriteria Optional LMD name-values pairs to incorporate into the search criteria.
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return list of qualifying MOs.
   * @throws RSuiteException
   */
  public static List<ManagedObject> searchForManagedObjects(User user, SearchService searchService,
      QName qname, boolean allowDescendants, List<NameValuesPair> lmdCriteria, int maxResultCount)
      throws RSuiteException {
    StringBuilder query = new StringBuilder(getXPathExpression(qname, allowDescendants));

    // LMD constraints
    if (lmdCriteria != null) {
      for (NameValuesPair lmdPair : lmdCriteria) {
        query.append(getLayeredMetadataXPathPredicate(lmdPair.getName(), lmdPair.getValues()));
      }
    }

    return searchForObjects(user, searchService, query.toString(), maxResultCount);
  }

  /**
   * Get a list of all CAs as MOs that match the specified LMD but exclude the specified ID.
   * 
   * @param user
   * @param searchService
   * @param caType Optional CA type to restrict results to.
   * @param lmdName Optional LMD name to work into the search criteria
   * @param lmdValue Optional LMD value to work into the search criteria
   * @param excludeId Optional CA ID to exclude from search
   * @return A list of MOs that are CAs
   * @throws RSuiteException
   */
  public static List<ManagedObject> searchForContentAssemblies(User user,
      SearchService searchService, String caType, String lmdName, String lmdValue, String excludeId)
      throws RSuiteException {
    return searchForContentAssemblies(user, searchService, caType, lmdName, lmdValue, excludeId, 0); // All
                                                                                                     // matches
  }

  /**
   * Get a list of CAs as MOs that match the specified LMD but exclude the specified ID.
   * 
   * @param user
   * @param searchService
   * @param caType Optional CA type to restrict results to.
   * @param lmdName Optional LMD name to work into the search criteria
   * @param lmdValue Optional LMD value to work into the search criteria
   * @param excludeId Optional CA ID to exclude from search
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return A list of MOs that are CAs
   * @throws RSuiteException
   */
  public static List<ManagedObject> searchForContentAssemblies(User user,
      SearchService searchService, String caType, String lmdName, String lmdValue, String excludeId,
      int maxResultCount) throws RSuiteException {
    return searchForContentAssemblies(user, searchService, caType, lmdName, new String[] {lmdValue},
        excludeId, maxResultCount);
  }

  /**
   * Get a list of CAs as MOs that match the specified LMD but exclude the specified ID.
   * 
   * @param user
   * @param searchService
   * @param caType Optional CA type to restrict results to.
   * @param lmdName Optional LMD name to work into the search criteria
   * @param lmdValues Optional LMD values to work into the search criteria
   * @param excludeId Optional CA ID to exclude from search
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return A list of MOs that are CAs
   * @throws RSuiteException
   */
  public static List<ManagedObject> searchForContentAssemblies(User user,
      SearchService searchService, String caType, String lmdName, String lmdValues[],
      String excludeId, int maxResultCount) throws RSuiteException {
    List<NameValuesPair> lmdCriteria = null;

    if (lmdValues != null && lmdValues.length > 0) {
      lmdCriteria = NameValuesPair.getStarterList(lmdName, lmdValues);
    }

    return searchForContentAssemblies(user, searchService, caType, lmdCriteria, excludeId, null,
        maxResultCount);
  }

  /**
   * Get a list of all CAs as MOs that match the specified CA type, and are sorted.
   * 
   * @param user
   * @param searchService
   * @param caType Optional CA type to restrict results to.
   * @param sortOrder Optional sort order. May send null in.
   * @return A sorted list of MOs that are CAs.
   * @throws RSuiteException
   */
  public static List<ManagedObject> searchForContentAssemblies(User user,
      SearchService searchService, String caType, List<SortOrder> sortOrder)
      throws RSuiteException {
    return searchForContentAssemblies(user, searchService, caType, null, null, sortOrder, 0);
  }

  /**
   * Search for a list of sorted CAs as MOs of the specified CA type and LMD, less the specified one
   * to excluded.
   * 
   * @param user
   * @param searchService
   * @param caType Optional CA type to restrict results to.
   * @param lmdCriteria Optional list of LMD name-value pairs to restrict results to.
   * @param excludeId Optional CA ID to exclude.
   * @param sortOrder Optional sort order. May send null in.
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return A sorted list of MOs that are CAs.
   * @throws RSuiteException
   */
  public static List<ManagedObject> searchForContentAssemblies(User user,
      SearchService searchService, String caType, List<NameValuesPair> lmdCriteria,
      String excludeId, List<SortOrder> sortOrder, int maxResultCount) throws RSuiteException {
    StringBuilder query = new StringBuilder(XPATH_ANY_CA);

    // ID constraint
    if (StringUtils.isNotBlank(excludeId)) {
      query.append(getSystemMetadataXPathPredicate(SystemMetadata.Id, "ne", excludeId.trim()));
    }

    // CA type constraint
    if (StringUtils.isNotBlank(caType)) {
      query.append(getSystemMetadataXPathPredicate(SystemMetadata.CAType, caType));
    }

    // LMD constraint
    if (lmdCriteria != null) {
      for (NameValuesPair lmdPair : lmdCriteria) {
        query.append(getLayeredMetadataXPathPredicate(lmdPair.getName(), lmdPair.getValues()));
      }
    }

    return searchForObjects(user, searchService, query.toString(), sortOrder, maxResultCount);
  }

  /**
   * Get a list of MOs that are content assemblies matching the specified type
   * <p>
   * Don't use this implementation if you expect many results. In that case,
   * SearchService#constructSearch() should be used.
   * 
   * @param user
   * @param searchService
   * @param caType Required
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return list of matching CA IDs.
   * @throws RSuiteException Throw if a parameter value is invalid, or RSuite encounters an
   *         exception with the search.
   */
  public static List<String> searchForContentAssemblyIds(User user, SearchService searchService,
      String caType, int maxResultCount) throws RSuiteException {
    return searchForContentAssemblyIds(user, searchService, caType, null, maxResultCount);
  }

  /**
   * Instance method to get a list of MOs that are content assemblies matching the specified type
   * and LMD.
   * <p>
   * Simply wraps
   * {@link #searchForContentAssemblyIds(User, SearchService, String, String, String, int)},
   * facilitating unit testing.
   * <p>
   * Don't use this implementation if you expect many results. In that case,
   * SearchService#constructSearch() should be used.
   * 
   * @param user
   * @param searchService
   * @param caType Required
   * @param lmdName Optional. Name of LMD to incorporate as search criteria.
   * @param lmdValue Optional. Value of LMD to incorporate as search criteria.
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return list of matching CA IDs.
   * @throws RSuiteException Throw if a parameter value is invalid, or RSuite encounters an
   *         exception with the search.
   */
  public List<String> iSearchForContentAssemblyIds(User user, SearchService searchService,
      String caType, String lmdName, String lmdValue, int maxResultCount) throws RSuiteException {
    return searchForContentAssemblyIds(user, searchService, caType, lmdName, lmdValue,
        maxResultCount);
  }

  /**
   * Get a list of MOs that are content assemblies matching the specified type and LMD
   * <p>
   * Don't use this implementation if you expect many results. In that case,
   * SearchService#constructSearch() should be used.
   * 
   * @param user
   * @param searchService
   * @param caType Required
   * @param lmdName Optional. Name of LMD to incorporate as search criteria.
   * @param lmdValue Optional. Value of LMD to incorporate as search criteria.
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return list of matching CA IDs.
   * @throws RSuiteException Throw if a parameter value is invalid, or RSuite encounters an
   *         exception with the search.
   */
  public static List<String> searchForContentAssemblyIds(User user, SearchService searchService,
      String caType, String lmdName, String lmdValue, int maxResultCount) throws RSuiteException {
    List<NameValuesPair> lmdCriteria = null;
    if (StringUtils.isNotBlank(lmdName) && StringUtils.isNotBlank(lmdValue)) {
      lmdCriteria = NameValuesPair.getStarterList(lmdName, lmdValue);
    }
    return searchForContentAssemblyIds(user, searchService, caType, lmdCriteria, maxResultCount);
  }

  /**
   * Get a list of MOs that are content assemblies matching the specified type and LMD
   * <p>
   * Don't use this implementation if you expect many results. In that case,
   * SearchService#constructSearch() should be used.
   * 
   * @param user
   * @param searchService
   * @param caType Required
   * @param lmdCriteria Optional. List of LMD name and value pairs to incorporate as search
   *        criteria. Repeating LMD supported.
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return list of matching CA IDs.
   * @throws RSuiteException Throw if a parameter value is invalid, or RSuite encounters an
   *         exception with the search.
   */
  public static List<String> searchForContentAssemblyIds(User user, SearchService searchService,
      String caType, List<NameValuesPair> lmdCriteria, int maxResultCount) throws RSuiteException {
    StringBuilder query = new StringBuilder(XPATH_ANY_CA);

    if (StringUtils.isBlank(caType))
      throw new RSuiteException("CA type is empty, but is required by this search.");
    // CA type constraint
    query.append(getSystemMetadataXPathPredicate(SystemMetadata.CAType, caType));

    // LMD constraints
    if (lmdCriteria != null) {
      for (NameValuesPair lmdPair : lmdCriteria) {
        query.append(getLayeredMetadataXPathPredicate(lmdPair.getName(), lmdPair.getValues()));
      }
    }

    return searchForObjectIds(user, searchService, query.toString(), maxResultCount);
  }

  /**
   * Search for the IDs of qualifying objects using an XPath expression.
   * <p>
   * The maximum number of search results imposed by SearchService#executeXPathSearch() is not
   * imposed by this method.
   * <p>
   * Default sort order applies.
   * 
   * @param user
   * @param searchService
   * @param query
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return A list of RSuite IDs to qualifying objects. Objects may include MOs and containers.
   * @throws RSuiteException Thrown if RSuite encounters an exception with the search.
   */
  public static List<String> searchForObjectIds(User user, SearchService searchService,
      String query, int maxResultCount) throws RSuiteException {
    return searchForObjectIds(user, searchService, query, null, maxResultCount);
  }

  /**
   * Search for the IDs of qualifying objects using an XPath expression.
   * <p>
   * The maximum number of search results imposed by SearchService#executeXPathSearch() is not
   * imposed by this method.
   * <p>
   * Default sort order applies.
   * 
   * @param user
   * @param searchService
   * @param query
   * @param sortOrder Optional sort order. May submit null.
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return A list of RSuite IDs to qualifying objects. Objects may include MOs and containers.
   * @throws RSuiteException Thrown if RSuite encounters an exception with the search.
   */
  public static List<String> searchForObjectIds(User user, SearchService searchService,
      String query, List<SortOrder> sortOrder, int maxResultCount) throws RSuiteException {
    List<String> ids = new ArrayList<String>();
    List<ManagedObject> moList =
        searchForObjects(user, searchService, query, sortOrder, maxResultCount);
    if (moList != null) {
      for (ManagedObject mo : moList) {
        ids.add(mo.getId());
      }
    }
    return ids;
  }

  /**
   * Execute an XPath-based search, whereby a purpose and maximum number of results may be
   * specified.
   * <p>
   * The maximum number of search results imposed by SearchService#executeXPathSearch() is not
   * imposed by this method.
   * <p>
   * Default sort order applies.
   * 
   * @param user
   * @param searchService
   * @param query
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return list of matching MO objects, which may include containers.
   * @throws RSuiteException Thrown if RSuite encounters an exception with the search.
   */
  public static List<ManagedObject> searchForObjects(User user, SearchService searchService,
      String query, int maxResultCount) throws RSuiteException {
    return searchForObjects(user, searchService, query, null, maxResultCount);
  }

  /**
   * Execute an XPath-based search, whereby a purpose and maximum number of results may be
   * specified.
   * <p>
   * The maximum number of search results imposed by SearchService#executeXPathSearch() is not
   * imposed by this method.
   * 
   * @param user
   * @param searchService
   * @param query
   * @param sortOrder Optional sort order. May submit null.
   * @param maxResultCount Indicate the maximum number of desired search results. For instance, if
   *        you only expect one, pass in two. This is an efficient way to get the one result you
   *        want, while also making sure there is only one. Send in 0 for all.
   * @return list of matching MO objects, which may include containers.
   * @throws RSuiteException Throw if RSuite encounters an exception with the search.
   */
  public static List<ManagedObject> searchForObjects(User user, SearchService searchService,
      String query, List<SortOrder> sortOrder, int maxResultCount) throws RSuiteException {

    // Perform search
    List<ManagedObject> results = new ArrayList<ManagedObject>();
    log.info("Submitting XPath search: " + query);
    Date start = new Date();
    try {
      Search search =
          searchService.constructSearch(user, RSuiteQueryType.XPATH, query, null, null, null, null);

      ContentDisplayObject item;
      int i = 0;
      while ((item = search.getResults().getResult(++i)) != null) {
        results.add(item.getManagedObject());
        if (maxResultCount > 0 && i > maxResultCount) {
          throw new RSuiteException(
              "Max result count threshold of " + maxResultCount + " exceeded.");
        }
        /*
         * By default, RSuite goes back to MarkLogic every 600 results. This can be overridden by
         * the rsuite.search.bucketSize property. Elected not to use that property's value at this
         * time.
         */
        if (i % 600 == 0) {
          log.info(new StringBuilder("Ongoing: collected ").append(i).append(" search results in ")
              .append(new Date().getTime() - start.getTime()).append(" millis").toString());
        }
      }
    } finally {
      log.info(new StringBuilder("Complete: collected ").append(results.size())
          .append(" search results in ").append(new Date().getTime() - start.getTime())
          .append(" millis").toString());
    }

    return results;
  }

}
