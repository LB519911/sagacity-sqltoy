/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.utils;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.callback.CallableStatementResultHandler;
import org.sagacity.sqltoy.callback.InsertRowCallbackHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.config.model.TableColumnMeta;
import org.sagacity.sqltoy.model.TreeTableModel;

/**
 * @project sagacity-sqltoy
 * @description 数据库sql相关的处理工具
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlUtil.java,Revision:v1.3,Date:Apr 14, 2009 11:52:31 PM
 * @Modification Date:2011-8-18
 *               {移植BaseDaoSupport中分页移植到SqlUtil中，将数据库表、外键、主键等库和表信息移植到DBUtil中 }
 * @Modification Date:2011-8-22 {修复getJdbcRecordCount中因group分组查询导致的错误， 如select
 *               name,count(*) from table group by name}
 * @Modification Date:2012-11-21
 *               {完善分页查询语句中存在union的处理机制,框架自动判断是否存在union,有union则自动实现外层包裹}
 * @Modification Date:2017-6-5 {剔除注释时用空白填补,防止出现类似原本:select xxx from 变成select
 *               xxxfrom }
 * @Modification $Date:2017-6-14 {修复针对阿里的druid数据库datasource针对clob类型处理的错误}
 * @Modification $Date:2019-7-5 剔除对druid clob bug的支持(druid 1.1.10 已经修复)
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class SqlUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LogManager.getLogger(SqlUtil.class);

	/**
	 * sql中的单行注释
	 */
	public final static Pattern maskPattern = Pattern.compile("\\/\\*[^(+|!)]");

	public static final Pattern ORDER_BY_PATTERN = Pattern.compile("(?i)\\Worder\\s+by\\W");

	public static final Pattern UPCASE_ORDER_PATTERN = Pattern.compile("\\WORder\\s+");

	/**
	 * 查询select 匹配
	 */
	public static final String SELECT_REGEX = "select\\s+";

	/**
	 * 查询from 匹配
	 */
	public static final String FROM_REGEX = "\\s+from[\\(|\\s+]";

	// union 匹配模式
	public static final Pattern UNION_PATTERN = Pattern.compile("(?i)\\W+union\\W+");

	// sql 注释过滤器
	private static HashMap sqlCommentfilters = new HashMap();

	static {
		// 排除表字段说明（注释）中的";"符号
		sqlCommentfilters.put("'", "'");
		sqlCommentfilters.put("(", ")");
		sqlCommentfilters.put("{", "}");
	}

	/**
	 * @todo 合成数据库in 查询的条件(不建议使用)
	 * @param conditions
	 *            :数据库in条件的数据集合，可以是POJO List或Object[]
	 * @param colIndex
	 *            :二维数组对应列编号
	 * @param property
	 *            :POJO property
	 * @param isChar
	 *            :in 是否要加单引号
	 * @return:example:1,2,3或'1','2','3'
	 * @throws Exception
	 */
	@Deprecated
	public static String combineQueryInStr(Object conditions, Integer colIndex, String property, boolean isChar)
			throws Exception {
		StringBuilder conditons = new StringBuilder(64);
		String flag = "";
		// 是否是字符类型
		if (isChar)
			flag = "'";
		// 判断数据集合维度
		int dimen = CollectionUtil.judgeObjectDimen(conditions);
		switch (dimen) {
		// 单个数据
		case 0: {
			conditons.append(flag).append(conditions.toString()).append(flag);
			break;
		}
		// 一维数组
		case 1: {
			Object[] array;
			if (conditions instanceof Collection) {
				array = ((Collection) conditions).toArray();
			} else if (conditions.getClass().isArray()) {
				array = CollectionUtil.convertArray(conditions);
			} else {
				array = ((Map) conditions).values().toArray();
			}
			for (int i = 0; i < array.length; i++) {
				if (i != 0) {
					conditons.append(",");
				}
				conditons.append(flag);
				if (null == property) {
					conditons.append(array[i]);
				} else {
					conditons.append(BeanUtils.getProperty(array[i], property));
				}
				conditons.append(flag);
			}
			break;
		}
		// 二维数据
		case 2: {
			Object[][] array;
			if (conditions instanceof Collection) {
				array = CollectionUtil.twoDimenlistToArray((Collection) conditions);
			} else if (conditions instanceof Object[][]) {
				array = (Object[][]) conditions;
			} else {
				array = CollectionUtil.twoDimenlistToArray(((Map) conditions).values());
			}
			for (int i = 0; i < array.length; i++) {
				if (i != 0) {
					conditons.append(",");
				}
				conditons.append(flag);
				if (null == property) {
					conditons.append(array[i][colIndex.intValue()]);
				} else {
					conditons.append(BeanUtils.getProperty(array[i][colIndex.intValue()], property));
				}
				conditons.append(flag);
			}
			break;
		}
		}
		return conditons.toString();
	}

	/**
	 * @todo 自动进行类型转换,设置sql中的参数条件的值
	 * @param conn
	 * @param pst
	 * @param params
	 * @param paramsType
	 * @param fromIndex
	 * @throws SQLException
	 * @throws IOException
	 */
	public static void setParamsValue(Connection conn, final Integer dbType, PreparedStatement pst, Object[] params,
			Integer[] paramsType, int fromIndex) throws SQLException, IOException {
		// fromIndex 针对存储过程调用存在从1开始,如:{?=call xxStore()}
		if (null != params && params.length > 0) {
			int n = params.length;
			if (null == paramsType || paramsType.length == 0) {
				// paramsType=-1 表示按照参数值来判断类型
				for (int i = 0; i < n; i++) {
					setParamValue(conn, dbType, pst, params[i], -1, fromIndex + 1 + i);
				}
			} else {
				for (int i = 0; i < n; i++) {
					setParamValue(conn, dbType, pst, params[i], paramsType[i], fromIndex + 1 + i);
				}
			}
		}
	}

	/**
	 * update 2017-6-14 修复使用druid数据库dataSource时clob处理的错误 update 2019-7-5 剔除对druid
	 * clob bug的支持(druid 1.1.10 已经修复)
	 * 
	 * @todo 设置sql中的参数条件的值
	 * @param conn
	 * @param pst
	 * @param paramValue
	 * @param jdbcType
	 * @param paramIndex
	 * @throws SQLException
	 * @throws IOException
	 */
	public static void setParamValue(Connection conn, final Integer dbType, PreparedStatement pst, Object paramValue,
			int jdbcType, int paramIndex) throws SQLException, IOException {
		// jdbc部分数据库赋null值时必须要指定数据类型
		String tmpStr;
		if (null == paramValue) {
			if (jdbcType != -1) {
				pst.setNull(paramIndex, jdbcType);
			} else {
				pst.setNull(paramIndex, java.sql.Types.NULL);
			}
		} else {
			if (paramValue instanceof java.lang.String) {
				tmpStr = (String) paramValue;
				if (jdbcType == java.sql.Types.CLOB) {
					Clob clob = conn.createClob();
					clob.setString(1, tmpStr);
					pst.setClob(paramIndex, clob);
				} else if (jdbcType == java.sql.Types.NCLOB) {
					NClob nclob = conn.createNClob();
					nclob.setString(1, tmpStr);
					pst.setNClob(paramIndex, nclob);
				} else {
					pst.setString(paramIndex, tmpStr);
				}
			} else if (paramValue instanceof java.lang.Integer) {
				pst.setInt(paramIndex, ((Integer) paramValue));
			} else if (paramValue instanceof java.lang.Double) {
				pst.setDouble(paramIndex, ((Double) paramValue));
			} else if (paramValue instanceof java.sql.Timestamp) {
				pst.setTimestamp(paramIndex, (java.sql.Timestamp) paramValue);
			} else if (paramValue instanceof java.sql.Time) {
				pst.setTime(paramIndex, (java.sql.Time) paramValue);
			} else if (paramValue instanceof java.util.Date) {
				pst.setTimestamp(paramIndex, new Timestamp(((java.util.Date) paramValue).getTime()));
			} else if (paramValue instanceof java.lang.Long) {
				pst.setLong(paramIndex, ((Long) paramValue));
			} else if (paramValue instanceof java.lang.Boolean) {
				pst.setBoolean(paramIndex, (Boolean) paramValue);
			} else if (paramValue instanceof BigDecimal) {
				pst.setBigDecimal(paramIndex, (BigDecimal) paramValue);
			} else if (paramValue instanceof java.sql.Clob) {
				tmpStr = clobToString((java.sql.Clob) paramValue);
				pst.setString(paramIndex, tmpStr);
			} else if (paramValue instanceof java.sql.Blob) {
				Blob tmp = (java.sql.Blob) paramValue;
				pst.setBytes(paramIndex, tmp.getBytes(0, Long.valueOf(tmp.length()).intValue()));
			} else if (paramValue instanceof java.sql.Date) {
				pst.setTimestamp(paramIndex, new Timestamp(((java.sql.Date) paramValue).getTime()));
			} else if (paramValue instanceof java.lang.Character) {
				tmpStr = ((Character) paramValue).toString();
				pst.setString(paramIndex, tmpStr);
			} else if (paramValue instanceof java.lang.Byte) {
				pst.setByte(paramIndex, (Byte) paramValue);
			} else if (paramValue instanceof byte[]) {
				if (jdbcType == java.sql.Types.BLOB) {
					Blob blob = null;
					try {
						blob = conn.createBlob();
						OutputStream out = blob.setBinaryStream(1);
						out.write((byte[]) paramValue);
						out.flush();
						out.close();
						pst.setBlob(paramIndex, blob);
					} catch (Exception e) {
						pst.setBytes(paramIndex, (byte[]) paramValue);
					}
				} else {
					pst.setBytes(paramIndex, (byte[]) paramValue);
				}
			} else if (paramValue instanceof java.lang.Short) {
				pst.setShort(paramIndex, (java.lang.Short) paramValue);
			} else if (paramValue instanceof java.lang.Float) {
				pst.setFloat(paramIndex, ((Float) paramValue));
			} else {
				if (jdbcType != -1) {
					pst.setObject(paramIndex, paramValue, jdbcType);
				} else {
					pst.setObject(paramIndex, paramValue);
				}
			}
		}
	}

	/**
	 * @todo <b>提供数据查询结果集转java对象的反射处理，以java VO集合形式返回</b>
	 * @param rs
	 * @param voClass
	 * @param ignoreAllEmptySet
	 * @return
	 * @throws Exception
	 */
	private static List reflectResultToValueObject(ResultSet rs, Class voClass, boolean ignoreAllEmptySet)
			throws Exception {
		List resultList = new ArrayList();
		// 提取数据预警阈值
		int warnThresholds = SqlToyConstants.getWarnThresholds();
		// 是否超出阈值
		boolean warnLimit = false;
		// 最大阀值
		long maxThresholds = SqlToyConstants.getMaxThresholds();
		boolean maxLimit = false;
		// 最大值要大于等于警告阀值
		if (maxThresholds > 1 && maxThresholds <= warnThresholds)
			maxThresholds = warnThresholds;
		// 获取voClass对象字段属性
		BeanInfo bi = Introspector.getBeanInfo(voClass);
		PropertyDescriptor[] pds = bi.getPropertyDescriptors();
		// 获取数据库查询结果中的字段信息
		List matchedFields = getPropertiesAndResultSetMatch(rs.getMetaData(), pds);
		int index = 0;
		// 循环通过java reflection将rs中的值映射到VO中
		Object rowTemp;
		while (rs.next()) {
			rowTemp = reflectResultRowToVOClass(rs, matchedFields, pds, voClass, ignoreAllEmptySet);
			if (rowTemp != null) {
				resultList.add(rowTemp);
			}
			index++;
			// 存在超出25000条数据的查询
			if (index == warnThresholds) {
				warnLimit = true;
			}
			// 超出最大提取数据阀值,直接终止数据提取
			if (index == maxThresholds) {
				maxLimit = true;
				break;
			}
		}
		// 提醒实际提取数量
		if (warnLimit) {
			logger.warn("Large Result:class={},total:{}>={}" + index, voClass.getName(), index, warnThresholds);
		}
		// 提醒实际提取数量
		if (maxLimit) {
			logger.warn("Large Result:class={},total:{}>={}" + index, voClass.getName(), index, maxThresholds);
		}
		return resultList;
	}

	/**
	 * @todo 提供数据查询结果集转java对象的反射处理，以java VO集合形式返回
	 * @param rs
	 * @param matchedFields
	 * @param pds
	 * @param voClass
	 * @param ignoreAllEmptySet
	 * @return
	 * @throws Exception
	 */
	private static Object reflectResultRowToVOClass(ResultSet rs, List matchedFields, PropertyDescriptor[] pds,
			Class voClass, boolean ignoreAllEmptySet) throws Exception {
		// 根据匹配的字段通过java reflection将rs中的值映射到VO中
		TableColumnMeta colMeta;
		Object bean = voClass.getDeclaredConstructor().newInstance();
		Object fieldValue;
		boolean allNull = true;
		for (int i = 0, n = matchedFields.size(); i < n; i++) {
			colMeta = (TableColumnMeta) matchedFields.get(i);
			if (colMeta.getDataType() == java.sql.Types.CLOB) {
				fieldValue = rs.getString(colMeta.getColName());
			} else {
				fieldValue = rs.getObject(colMeta.getColName());
			}
			if (null != fieldValue) {
				allNull = false;
				// java 反射调用
				pds[colMeta.getColIndex()].getWriteMethod().invoke(bean,
						// rs对象类型转java对象类型
						BeanUtil.convertType(fieldValue, colMeta.getTypeName()));
			}
		}
		if (allNull && ignoreAllEmptySet) {
			return null;
		}
		return bean;
	}

	/**
	 * @todo 获取VO属性和ResultSet 字段之间的对照关系
	 * @param rsmd
	 * @param pds
	 * @return
	 */
	private static List getPropertiesAndResultSetMatch(ResultSetMetaData rsmd, PropertyDescriptor[] pds)
			throws Exception {
		List matchedFields = new ArrayList();
		// 获取数据库查询结果中的字段信息
		HashMap colsHash = new HashMap();
		int fieldCnt = rsmd.getColumnCount();
		String colName;
		for (int i = 1; i < fieldCnt + 1; i++) {
			colName = rsmd.getColumnLabel(i);
			// 剔除数据库字段中的"_"符号
			colsHash.put(colName.replaceAll("_", "").toLowerCase(), colName);
		}
		String property;
		// 提取java对象属性跟数据库结果集字段属性一致的放入List中
		for (int i = 0, n = pds.length; i < n; i++) {
			property = pds[i].getName();
			colName = (String) colsHash.get(property.toLowerCase());
			// vo 属性跟数据库查询结果集字段匹配
			if (null != colName) {
				TableColumnMeta colMeta = new TableColumnMeta();
				colMeta.setColName(colName);
				colMeta.setColIndex(i);
				colMeta.setAliasName(property);
				colMeta.setTypeName(pds[i].getPropertyType().getName());
				matchedFields.add(colMeta);
			}
		}
		return matchedFields;
	}

	/**
	 * @todo 提供统一的ResultSet,PreparedStatemenet 关闭功能
	 * @param userData
	 * @param pst
	 * @param rs
	 * @param preparedStatementResultHandler
	 * @return
	 */
	public static Object preparedStatementProcess(Object userData, PreparedStatement pst, ResultSet rs,
			PreparedStatementResultHandler preparedStatementResultHandler) throws Exception {
		try {
			preparedStatementResultHandler.execute(userData, pst, rs);
		} catch (Exception se) {
			logger.error(se.getMessage(), se);
			throw se;
		} finally {
			try {
				if (rs != null) {
					rs.close();
					rs = null;
				}
				if (pst != null) {
					pst.close();
					pst = null;
				}
			} catch (SQLException se) {
				se.printStackTrace();
			}
		}
		return preparedStatementResultHandler.getResult();
	}

	/**
	 * @todo 提供统一的ResultSet,callableStatement 关闭功能
	 * @param userData
	 * @param pst
	 * @param rs
	 * @param callableStatementResultHandler
	 * @return
	 */
	public static Object callableStatementProcess(Object userData, CallableStatement pst, ResultSet rs,
			CallableStatementResultHandler callableStatementResultHandler) throws Exception {
		try {
			callableStatementResultHandler.execute(userData, pst, rs);
		} catch (Exception se) {
			logger.error(se.getMessage(), se);
			throw se;
		} finally {
			try {
				if (rs != null) {
					rs.close();
					rs = null;
				}
				if (pst != null) {
					pst.close();
					pst = null;
				}
			} catch (SQLException se) {
				se.printStackTrace();
			}
		}
		return callableStatementResultHandler.getResult();
	}

	/**
	 * @todo 剔除sql中的注释(提供三种形态的注释剔除)
	 * @param sql
	 * @return
	 */
	public static String clearMark(String sql) {
		if (StringUtil.isBlank(sql))
			return sql;
		int endMarkIndex;
		// 剔除<!-- -->形式的多行注释
		int markIndex = sql.indexOf("<!--");
		while (markIndex != -1) {
			endMarkIndex = sql.indexOf("-->", markIndex);
			if (endMarkIndex == -1 || endMarkIndex == sql.length() - 3) {
				sql = sql.substring(0, markIndex);
				break;
			} else {
				// update 2017-6-5
				sql = sql.substring(0, markIndex).concat(" ").concat(sql.substring(endMarkIndex + 3));
			}
			markIndex = sql.indexOf("<!--");
		}
		// 剔除/* */形式的多行注释(如果是/*+ALL_ROWS*/ 或 /*! ALL_ROWS*/形式的诸如oracle hint的用法不看作是注释)
		markIndex = StringUtil.matchIndex(sql, maskPattern);
		while (markIndex != -1) {
			endMarkIndex = sql.indexOf("*/", markIndex);
			if (endMarkIndex == -1 || endMarkIndex == sql.length() - 2) {
				sql = sql.substring(0, markIndex);
				break;
			} else {
				// update 2017-6-5
				sql = sql.substring(0, markIndex).concat(" ").concat(sql.substring(endMarkIndex + 2));
			}
			markIndex = StringUtil.matchIndex(sql, maskPattern);
		}
		// 剔除单行注释
		markIndex = sql.indexOf("--");
		while (markIndex != -1) {
			// 换行符号
			endMarkIndex = sql.indexOf("\n", markIndex);
			if (endMarkIndex == -1 || endMarkIndex == sql.length() - 1) {
				sql = sql.substring(0, markIndex);
				break;
			} else {
				// update 2017-6-5 增加concat(" ")避免因换行导致sql语句直接相连
				sql = sql.substring(0, markIndex).concat(" ").concat(sql.substring(endMarkIndex + 1));
			}
			markIndex = sql.indexOf("--");
		}
		// 剔除sql末尾的分号逗号(开发过程中容易忽视)
		if (sql.endsWith(";") || sql.endsWith(",")) {
			sql = sql.substring(0, sql.length() - 1);
		}
		// 剔除全角
		return sql.replaceAll("\\：", ":").replaceAll("\\＝", "=").replaceAll("\\．", ".");
	}

	/**
	 * @todo <b>获取单条记录</b>
	 * @param queryStr
	 * @param params
	 * @param voClass
	 * @param rowCallbackHandler
	 * @param conn
	 * @param ignoreAllEmptySet
	 * @return
	 * @throws Exception
	 */
	public static Object loadByJdbcQuery(final String queryStr, final Object[] params, final Class voClass,
			final RowCallbackHandler rowCallbackHandler, final Connection conn, final Integer dbType,
			final boolean ignoreAllEmptySet) throws Exception {
		List result = findByJdbcQuery(queryStr, params, voClass, rowCallbackHandler, conn, dbType, ignoreAllEmptySet);
		if (result != null && !result.isEmpty()) {
			if (result.size() > 1) {
				throw new IllegalAccessException("查询结果不唯一,loadByJdbcQuery 方法只针对单条结果的数据查询!");
			}
			return result.get(0);
		}
		return null;
	}

	/**
	 * @todo <b>sql 查询并返回List集合结果</b>
	 * @param queryStr
	 * @param params
	 * @param voClass
	 * @param rowCallbackHandler
	 * @param conn
	 * @param ignoreAllEmptySet
	 * @return
	 * @throws Exception
	 */
	public static List findByJdbcQuery(final String queryStr, final Object[] params, final Class voClass,
			final RowCallbackHandler rowCallbackHandler, final Connection conn, final Integer dbType,
			final boolean ignoreAllEmptySet) throws Exception {
		ResultSet rs = null;
		PreparedStatement pst = conn.prepareStatement(queryStr, ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY);
		List result = (List) preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws Exception {
				setParamsValue(conn, dbType, pst, params, null, 0);
				rs = pst.executeQuery();
				this.setResult(processResultSet(rs, voClass, rowCallbackHandler, 0, ignoreAllEmptySet));
			}
		});
		// 为null返回一个空集合
		if (result == null) {
			result = new ArrayList();
		}
		return result;
	}

	/**
	 * @todo 处理sql查询时的结果集,当没有反调或voClass反射处理时以数组方式返回resultSet的数据
	 * @param rs
	 * @param voClass
	 * @param rowCallbackHandler
	 * @param startColIndex
	 * @param ignoreAllEmptySet
	 * @return
	 * @throws Exception
	 */
	public static List processResultSet(ResultSet rs, Class voClass, RowCallbackHandler rowCallbackHandler,
			int startColIndex, boolean ignoreAllEmptySet) throws Exception {
		// 记录行记数器
		int index = 0;
		// 提取数据预警阈值
		int warnThresholds = SqlToyConstants.getWarnThresholds();
		// 是否超出阈值
		boolean warnLimit = false;
		// 最大阀值
		long maxThresholds = SqlToyConstants.getMaxThresholds();
		boolean maxLimit = false;
		// 最大值要大于等于警告阀值
		if (maxThresholds > 1 && maxThresholds <= warnThresholds)
			maxThresholds = warnThresholds;
		List result;
		if (voClass != null) {
			result = reflectResultToValueObject(rs, voClass, ignoreAllEmptySet);
		} else if (rowCallbackHandler != null) {
			while (rs.next()) {
				rowCallbackHandler.processRow(rs, index);
				index++;
				// 超出预警阀值
				if (index == warnThresholds)
					warnLimit = true;
				// 提取数据超过上限(-1表示不限制)
				if (index == maxThresholds) {
					maxLimit = true;
					break;
				}
			}
			result = rowCallbackHandler.getResult();
		} else {
			// 取得字段列数,在没有rowCallbackHandler用数组返回
			int rowCnt = rs.getMetaData().getColumnCount();
			List items = new ArrayList();
			Object fieldValue = null;
			boolean allNull = true;
			while (rs.next()) {
				allNull = true;
				List rowData = new ArrayList();
				for (int i = startColIndex; i < rowCnt; i++) {
					// 处理clob
					fieldValue = rs.getObject(i + 1);
					if (fieldValue != null) {
						allNull = false;
						if (fieldValue instanceof java.sql.Clob)
							fieldValue = clobToString((java.sql.Clob) fieldValue);
					}
					rowData.add(fieldValue);
				}
				if (!(allNull && ignoreAllEmptySet)) {
					items.add(rowData);
				}
				index++;
				// 超出预警阀值
				if (index == warnThresholds) {
					warnLimit = true;
				}
				// 超出最大提取数据阀值,直接终止数据提取
				if (index == maxThresholds) {
					maxLimit = true;
					break;
				}
			}
			result = items;
		}
		// 提醒实际提取数据量
		if (warnLimit) {
			logger.warn("Large Result:total={}>={}", index, warnThresholds);
		}
		// 超过最大提取数据阀值
		if (maxLimit) {
			logger.error("Max Large Result:total={}>={}", index, maxThresholds);
		}
		return result;
	}

	/**
	 * @todo 通过jdbc方式批量插入数据，一般提供给数据采集时或插入临时表使用，一般采用hibernate 方式插入
	 * @param updateSql
	 * @param rowDatas
	 * @param batchSize
	 * @param insertCallhandler
	 * @param updateTypes
	 * @param autoCommit
	 * @param conn
	 * @throws Exception
	 */
	public static Long batchUpdateByJdbc(final String updateSql, final Collection rowDatas, final int batchSize,
			final InsertRowCallbackHandler insertCallhandler, final Integer[] updateTypes, final Boolean autoCommit,
			final Connection conn, final Integer dbType) throws Exception {
		if (rowDatas == null) {
			logger.error("执行batchUpdateByJdbc 数据为空，sql={}", updateSql);
			return 0L;
		}
		PreparedStatement pst = null;
		long updateCount = 0;
		try {
			boolean hasSetAutoCommit = false;
			boolean useCallHandler = true;
			// 是否使用反调方式
			if (insertCallhandler == null)
				useCallHandler = false;
			// 是否自动提交
			if (autoCommit != null && autoCommit.booleanValue() != conn.getAutoCommit()) {
				conn.setAutoCommit(autoCommit.booleanValue());
				hasSetAutoCommit = true;
			}
			pst = conn.prepareStatement(updateSql);
			int totalRows = rowDatas.size();
			boolean useBatch = (totalRows > 1) ? true : false;
			Object rowData;
			int index = 0;
			// 批处理计数器
			int meter = 0;
			for (Iterator iter = rowDatas.iterator(); iter.hasNext();) {
				rowData = iter.next();
				index++;
				if (rowData != null) {
					// 使用反调
					if (useCallHandler) {
						insertCallhandler.process(pst, index, rowData);
					} else {
						// 使用对象properties方式传值
						if (rowData.getClass().isArray()) {
							Object[] tmp = CollectionUtil.convertArray(rowData);
							for (int i = 0; i < tmp.length; i++) {
								setParamValue(conn, dbType, pst, tmp[i], updateTypes == null ? -1 : updateTypes[i],
										i + 1);
							}
						} else if (rowData instanceof Collection) {
							Collection tmp = (Collection) rowData;
							int tmpIndex = 0;
							for (Iterator tmpIter = tmp.iterator(); tmpIter.hasNext();) {
								setParamValue(conn, dbType, pst, tmpIter.next(),
										updateTypes == null ? -1 : updateTypes[tmpIndex], tmpIndex + 1);
								tmpIndex++;
							}
						}
					}
					meter++;
					if (useBatch) {
						pst.addBatch();
						if ((meter % batchSize) == 0 || index == totalRows) {
							int[] updateRows = pst.executeBatch();
							for (int t : updateRows) {
								updateCount = updateCount + ((t > 0) ? t : 0);
							}
							pst.clearBatch();
						}
					} else {
						pst.execute();
						updateCount = updateCount + ((pst.getUpdateCount() > 0) ? pst.getUpdateCount() : 0);
					}
				}
			}
			if (hasSetAutoCommit) {
				conn.setAutoCommit(!autoCommit);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		} finally {
			try {
				if (pst != null) {
					pst.close();
					pst = null;
				}
			} catch (SQLException se) {
				logger.error(se.getMessage(), se);
			}
		}
		return updateCount;
	}

	/**
	 * @todo 计算树形结构表中的:节点层级、节点对应所有上级节点的路径、是否叶子节点
	 * @param treeTableModel
	 * @param conn
	 * @return
	 * @throws Exception
	 */
	public static boolean wrapTreeTableRoute(final TreeTableModel treeTableModel, Connection conn, final Integer dbType)
			throws Exception {
		if (StringUtil.isBlank(treeTableModel.getTableName()) || StringUtil.isBlank(treeTableModel.getIdField())
				|| StringUtil.isBlank(treeTableModel.getPidField())) {
			logger.error("请设置树形表的table名称、id字段名称、pid字段名称!");
			throw new IllegalArgumentException("没有对应的table名称、id字段名称、pid字段名称");
		}
		String flag = "";
		// 判断是否字符串类型
		if (treeTableModel.isChar()) {
			flag = "'";
		}
		// 修改nodeRoute和nodeLevel
		if (StringUtil.isNotBlank(treeTableModel.getNodeRouteField())
				&& StringUtil.isNotBlank(treeTableModel.getNodeLevelField())) {
			StringBuilder nextNodeQueryStr = new StringBuilder("select ").append(treeTableModel.getIdField())
					.append(",").append(treeTableModel.getNodeRouteField()).append(",")
					.append(treeTableModel.getPidField()).append(" from ").append(treeTableModel.getTableName())
					.append(" where ").append(treeTableModel.getPidField()).append(" in (${inStr})");
			String idInfoSql = "select ".concat(treeTableModel.getNodeLevelField()).concat(",")
					.concat(treeTableModel.getNodeRouteField()).concat(" from ").concat(treeTableModel.getTableName())
					.concat(" where ").concat(treeTableModel.getIdField()).concat("=").concat(flag)
					.concat(treeTableModel.getRootId().toString()).concat(flag);
			if (StringUtil.isNotBlank(treeTableModel.getConditions())) {
				idInfoSql = idInfoSql.concat(" and ").concat(treeTableModel.getConditions());
			}
			// 获取层次等级
			List idInfo = findByJdbcQuery(idInfoSql, null, null, null, conn, dbType, false);
			// 设置第一层level
			int nodeLevel = 0;
			String nodeRoute = "";
			if (idInfo != null && !idInfo.isEmpty()) {
				nodeLevel = Integer.parseInt(((List) idInfo.get(0)).get(0).toString());
				nodeRoute = ((List) idInfo.get(0)).get(1).toString();
			}
			StringBuilder updateLevelAndRoute = new StringBuilder("update ").append(treeTableModel.getTableName())
					.append(" set ").append(treeTableModel.getNodeLevelField()).append("=?,")
					.append(treeTableModel.getNodeRouteField()).append("=? ").append(" where ")
					.append(treeTableModel.getIdField()).append("=?");
			if (StringUtil.isNotBlank(treeTableModel.getConditions())) {
				nextNodeQueryStr.append(" and ").append(treeTableModel.getConditions());
				updateLevelAndRoute.append(" and ").append(treeTableModel.getConditions());
			}

			// 模拟指定节点的信息
			HashMap pidsMap = new HashMap();
			pidsMap.put(treeTableModel.getRootId().toString(), nodeRoute);
			// 下级节点
			List ids;
			if (treeTableModel.getIdValue() != null) {
				StringBuilder firstNextNodeQuery = new StringBuilder("select ").append(treeTableModel.getIdField())
						.append(",").append(treeTableModel.getNodeRouteField()).append(",")
						.append(treeTableModel.getPidField()).append(" from ").append(treeTableModel.getTableName())
						.append(" where ").append(treeTableModel.getIdField()).append("=?");
				if (StringUtil.isNotBlank(treeTableModel.getConditions())) {
					firstNextNodeQuery.append(" and ").append(treeTableModel.getConditions());
				}
				ids = findByJdbcQuery(firstNextNodeQuery.toString(), new Object[] { treeTableModel.getIdValue() }, null,
						null, conn, dbType, false);
			} else {
				ids = findByJdbcQuery(nextNodeQueryStr.toString().replaceFirst("\\$\\{inStr\\}",
						flag + treeTableModel.getRootId() + flag), null, null, null, conn, dbType, false);
			}
			if (ids != null && !ids.isEmpty()) {
				processNextLevel(updateLevelAndRoute.toString(), nextNodeQueryStr.toString(), treeTableModel, pidsMap,
						ids, nodeLevel + 1, conn, dbType);
			}
		}
		// 设置节点是否为叶子节点，（mysql不支持update table where in 机制）
		if (StringUtil.isNotBlank(treeTableModel.getLeafField())) {
			// 将所有记录先全部设置为叶子节点(isLeaf=1)
			StringBuilder updateLeafSql = new StringBuilder();
			updateLeafSql.append("update ").append(treeTableModel.getTableName());
			updateLeafSql.append(" set ").append(treeTableModel.getLeafField()).append("=1");
			// 附加条件(保留)
			if (StringUtil.isNotBlank(treeTableModel.getConditions())) {
				updateLeafSql.append(" where ").append(treeTableModel.getConditions());
			}
			executeSql(updateLeafSql.toString(), null, null, conn, dbType, true);

			// 设置被设置为父节点的记录为非叶子节点(isLeaf=0)
			StringBuilder updateTrunkLeafSql = new StringBuilder();
			updateTrunkLeafSql.append("update ").append(treeTableModel.getTableName());
			// int dbType = DataSourceUtils.getDbType(conn);
			// 支持mysql8 update 2018-5-11
			if (dbType == DataSourceUtils.DBType.MYSQL || dbType == DataSourceUtils.DBType.MYSQL8) {
				// update sys_organ_info a inner join (select t.organ_pid from
				// sys_organ_info t) b
				// on a.organ_id=b.organ_pid set IS_LEAF=0
				// set field=value
				updateTrunkLeafSql.append(" inner join (select ");
				updateTrunkLeafSql.append(treeTableModel.getPidField());
				updateTrunkLeafSql.append(" from ").append(treeTableModel.getTableName());
				if (StringUtil.isNotBlank(treeTableModel.getConditions())) {
					updateTrunkLeafSql.append(" where ").append(treeTableModel.getConditions());
				}
				updateTrunkLeafSql.append(") as t_wrapLeaf ");
				updateTrunkLeafSql.append(" on ");
				updateTrunkLeafSql.append(treeTableModel.getIdField()).append("=t_wrapLeaf.")
						.append(treeTableModel.getPidField());
				updateTrunkLeafSql.append(" set ");
				updateTrunkLeafSql.append(treeTableModel.getLeafField()).append("=0");
				if (StringUtil.isNotBlank(treeTableModel.getConditions())) {
					updateTrunkLeafSql.append(" where ").append(treeTableModel.getConditions());
				}
			} else {
				// update organ_info set IS_LEAF=0
				// where organ_id in (select organ_pid from organ_info)
				updateTrunkLeafSql.append(" set ");
				updateTrunkLeafSql.append(treeTableModel.getLeafField()).append("=0");
				updateTrunkLeafSql.append(" where ").append(treeTableModel.getIdField());
				updateTrunkLeafSql.append(" in (select ").append(treeTableModel.getPidField());
				updateTrunkLeafSql.append(" from ").append(treeTableModel.getTableName());
				if (StringUtil.isNotBlank(treeTableModel.getConditions())) {
					updateTrunkLeafSql.append(" where ").append(treeTableModel.getConditions());
				}
				updateTrunkLeafSql.append(") ");
				if (StringUtil.isNotBlank(treeTableModel.getConditions())) {
					updateTrunkLeafSql.append(" and ").append(treeTableModel.getConditions());
				}
			}
			executeSql(updateTrunkLeafSql.toString(), null, null, conn, dbType, true);
		}
		return true;
	}

	/**
	 * @todo TreeTableRoute中处理下一层级的递归方法，逐层计算下一级节点的节点层次和路径
	 * @param updateLevelAndRoute
	 * @param nextNodeQueryStr
	 * @param treeTableModel
	 * @param pidsMap
	 * @param ids
	 * @param nodeLevel
	 * @param conn
	 * @throws Exception
	 */
	private static void processNextLevel(final String updateLevelAndRoute, final String nextNodeQueryStr,
			final TreeTableModel treeTableModel, final HashMap pidsMap, List ids, final int nodeLevel, Connection conn,
			final int dbType) throws Exception {
		// 修改节点level和节点路径
		batchUpdateByJdbc(updateLevelAndRoute, ids, 500, new InsertRowCallbackHandler() {
			public void process(PreparedStatement pst, int index, Object rowData) throws SQLException {
				String id = ((List) rowData).get(0).toString();
				// 获得父节点id和父节点路径
				String pid = ((List) rowData).get(2).toString();
				String nodeRoute = (String) pidsMap.get(pid);
				int size = treeTableModel.getIdLength();
				if (nodeRoute == null || nodeRoute.trim().equals("")) {
					nodeRoute = "";
					if (!treeTableModel.isChar() || treeTableModel.isAppendZero()) {
						// 负数
						if (CommonUtils.isInteger(pid) && pid.indexOf("-") == 0) {
							nodeRoute = nodeRoute.concat("-")
									.concat(StringUtil.addLeftZero2Len(pid.substring(1), size - 1));
						} else {
							nodeRoute = nodeRoute.concat(StringUtil.addLeftZero2Len(pid, size));
						}
					} else {
						nodeRoute = nodeRoute.concat(StringUtil.addRightBlank2Len(pid, size));
					}
				} else {
					nodeRoute = nodeRoute.trim();
				}
				// update 2018-1-9 增加判断是否以逗号结尾,解决修改过程中出现双逗号问题
				if (!nodeRoute.endsWith(treeTableModel.getSplitSign())) {
					nodeRoute = nodeRoute.concat(treeTableModel.getSplitSign());
				}
				// 回置节点的nodeRoute值
				if (!treeTableModel.isChar() || treeTableModel.isAppendZero()) {
					nodeRoute = nodeRoute.concat(StringUtil.addLeftZero2Len(id, size));
				} else {
					nodeRoute = nodeRoute.concat(StringUtil.addRightBlank2Len(id, size));
				}

				((List) rowData).set(1, nodeRoute);
				// 节点等级
				pst.setInt(1, nodeLevel);
				// 节点路径(当节点路径长度不做补充统一长度操作,则末尾自动加上一个分割符)
				pst.setString(2, nodeRoute + ((size < 2) ? treeTableModel.getSplitSign() : ""));

				if (treeTableModel.isChar()) {
					pst.setString(3, id);
				} else {
					pst.setLong(3, Long.parseLong(id));
				}
			}
		}, null, false, conn, dbType);

		// 处理节点的下一层次
		int size = ids.size();
		int fromIndex = 0;
		int toIndex = -1;

		// 避免in()中的参数过多，每次500个
		String inStrs;
		List subIds = null;
		List nextIds = null;
		boolean exist = false;
		while (toIndex < size) {
			fromIndex = toIndex + 1;
			toIndex += 500;
			if (toIndex >= size - 1) {
				toIndex = size - 1;
				exist = true;
			}
			if (fromIndex >= toIndex) {
				subIds = new ArrayList();
				subIds.add(ids.get(toIndex));
			} else {
				subIds = ids.subList(fromIndex, toIndex + 1);
			}
			inStrs = combineQueryInStr(subIds, 0, null, treeTableModel.isChar());

			// 获取下一层节点
			nextIds = findByJdbcQuery(nextNodeQueryStr.replaceFirst("\\$\\{inStr\\}", inStrs), null, null, null, conn,
					dbType, false);
			// 递归处理下一层
			if (nextIds != null && !nextIds.isEmpty()) {
				processNextLevel(updateLevelAndRoute, nextNodeQueryStr, treeTableModel,
						CollectionUtil.hashList(subIds, 0, 1, true), nextIds, nodeLevel + 1, conn, dbType);
			}
			if (exist) {
				break;
			}
		}
	}

	/**
	 * @todo <b>sql文件自动创建到数据库</b>
	 * @param conn
	 * @param sqlContent
	 * @param batchSize
	 * @param autoCommit
	 * @throws Exception
	 */
	public static void executeBatchSql(Connection conn, String sqlContent, Integer batchSize, Boolean autoCommit)
			throws Exception {
		String splitSign = DataSourceUtils.getDatabaseSqlSplitSign(conn);
		// 剔除sql中的注释
		sqlContent = SqlUtil.clearMark(sqlContent);
		if (splitSign.indexOf("go") != -1) {
			sqlContent = StringUtil.clearMistyChars(sqlContent, " ");
		}
		// sqlserver sybase 数据库以go 分割,则整个sql文件作为一个语句执行
		String[] statments = StringUtil.splitExcludeSymMark(sqlContent, splitSign, sqlCommentfilters);
		boolean hasSetAutoCommit = false;
		// 是否自动提交
		if (autoCommit != null && autoCommit.booleanValue() != conn.getAutoCommit()) {
			conn.setAutoCommit(autoCommit.booleanValue());
			hasSetAutoCommit = true;
		}
		Statement stat = null;
		try {
			stat = conn.createStatement();
			int meter = 0;
			int realBatch = (batchSize == null || batchSize.intValue() > 1) ? batchSize.intValue() : 100;
			int totalRows = statments.length;
			int i = 0;
			for (String sql : statments) {
				if (StringUtil.isNotBlank(sql)) {
					meter++;
					logger.debug("正在批量执行的sql:{}", sql);
					stat.addBatch(sql);
				}
				if ((meter % realBatch) == 0 || i + 1 == totalRows) {
					stat.executeBatch();
					stat.clearBatch();
				}
				i++;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		} finally {
			if (stat != null) {
				stat.close();
				stat = null;
			}
		}
		// 恢复conn原始autoCommit默认值
		if (hasSetAutoCommit)
			conn.setAutoCommit(!autoCommit);
	}

	/**
	 * @todo <b>判断sql语句中是否有order by排序</b>
	 * @param sql
	 * @param judgeUpcase
	 * @return
	 */
	public static boolean hasOrderBy(String sql, boolean judgeUpcase) {
		// 最后的收括号位置
		int lastBracketIndex = sql.lastIndexOf(")");
		boolean result = false;
		int orderByIndex = StringUtil.matchLastIndex(sql, ORDER_BY_PATTERN);
		// 存在order by
		if (orderByIndex > lastBracketIndex)
			result = true;
		// 特殊处理 order by，通过ORder这种非常规写法代表
		if (judgeUpcase) {
			int upcaseOrderBy = StringUtil.matchLastIndex(sql, UPCASE_ORDER_PATTERN);
			if (upcaseOrderBy > lastBracketIndex)
				result = false;
		}
		return result;
	}

	/**
	 * @todo clob转换成字符串
	 * @param clob
	 * @return
	 */
	public static String clobToString(Clob clob) {
		if (clob == null)
			return null;
		StringBuffer sb = new StringBuffer(1024 * 8);// 8K
		Reader clobStream = null;
		try {
			clobStream = clob.getCharacterStream();
			char[] b = new char[1024];// 每次获取1K
			int i = 0;
			while ((i = clobStream.read(b)) != -1) {
				sb.append(b, 0, i);
			}
		} catch (Exception ex) {
			sb = null;
		} finally {
			closeQuietly(clobStream);
		}
		if (sb == null)
			return null;
		else
			return sb.toString();
	}

	/**
	 * @todo 执行Sql语句完成修改操作
	 * @param executeSql
	 * @param params
	 * @param paramsType
	 * @param conn
	 * @param autoCommit
	 * @throws Exception
	 */
	public static Long executeSql(final String executeSql, final Object[] params, final Integer[] paramsType,
			final Connection conn, final Integer dbType, final Boolean autoCommit) throws Exception {
		logger.debug("executeJdbcSql={}", executeSql);
		boolean hasSetAutoCommit = false;
		Long updateCounts = null;
		if (autoCommit != null) {
			if (!autoCommit == conn.getAutoCommit()) {
				conn.setAutoCommit(autoCommit);
				hasSetAutoCommit = true;
			}
		}
		PreparedStatement pst = conn.prepareStatement(executeSql);
		Object result = SqlUtil.preparedStatementProcess(null, pst, null, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				SqlUtil.setParamsValue(conn, dbType, pst, params, paramsType, 0);
				pst.executeUpdate();
				// 返回update的记录数量
				this.setResult(Long.valueOf(pst.getUpdateCount()));
			}
		});
		if (result != null)
			updateCounts = (Long) result;
		if (hasSetAutoCommit && autoCommit != null)
			conn.setAutoCommit(!autoCommit);
		return updateCounts;
	}

	/**
	 * @todo 转换主键数据类型
	 * @param idValue
	 * @param jdbcType
	 * @return
	 */
	public static Object convertIdValueType(Object idValue, int jdbcType) {
		if (idValue == null)
			return null;
		switch (jdbcType) {
		case java.sql.Types.VARCHAR:
		case java.sql.Types.NCHAR:
		case java.sql.Types.NVARCHAR:
		case java.sql.Types.CHAR:
			return idValue.toString();
		case java.sql.Types.BIGINT:
			return Long.valueOf(idValue.toString());
		case java.sql.Types.DECIMAL:
		case java.sql.Types.NUMERIC:
			return new BigDecimal(idValue.toString());
		case java.sql.Types.TINYINT:
		case java.sql.Types.INTEGER:
		case java.sql.Types.SMALLINT:
			return Integer.valueOf(idValue.toString());
		default:
			return idValue;
		}
	}

	/**
	 * @todo 关闭一个或多个流对象
	 * @param closeables
	 *            可关闭的流对象列表
	 * @throws IOException
	 */
	public static void close(Closeable... closeables) throws IOException {
		if (closeables != null) {
			for (Closeable closeable : closeables) {
				if (closeable != null) {
					closeable.close();
				}
			}
		}
	}

	/**
	 * @todo 关闭一个或多个流对象
	 * @param closeables
	 *            可关闭的流对象列表
	 */
	public static void closeQuietly(Closeable... closeables) {
		try {
			close(closeables);
		} catch (IOException e) {
			// do nothing
		}
	}

	/**
	 * @todo 判断是否内包含union 查询,即是否是select * from (select * from t union select * from
	 *       t2 ) 形式的查询,将所有()剔除后判定是否有union 存在
	 * @param sql
	 * @param clearMistyChar
	 * @return
	 */
	public static boolean hasUnion(String sql, boolean clearMistyChar) {
		StringBuilder lastSql = new StringBuilder(clearMistyChar ? StringUtil.clearMistyChars(sql, " ") : sql);
		// 找到第一个select 所对称的from位置，排查掉子查询中的内容
		int fromIndex = StringUtil.getSymMarkMatchIndex(SELECT_REGEX, FROM_REGEX, sql.toLowerCase(), 0);
		if (fromIndex != -1) {
			lastSql.delete(0, fromIndex);
		}
		// 删除所有对称的括号中的内容
		int start = lastSql.indexOf("(");
		int symMarkEnd;
		while (start != -1) {
			symMarkEnd = StringUtil.getSymMarkIndex("(", ")", lastSql.toString(), start);
			if (symMarkEnd != -1) {
				lastSql.delete(start, symMarkEnd + 1);
				start = lastSql.indexOf("(");
			} else
				break;
		}
		if (StringUtil.matches(lastSql.toString(), UNION_PATTERN))
			return true;
		return false;
	}
}
