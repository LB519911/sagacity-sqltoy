/**
 *@Generated by sagacity-quickvo 4.0
 */
package sqltoy.showcase.sagacity.vo.base;

import java.io.Serializable;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.annotation.Column;
import java.util.Date;

import org.sagacity.sqltoy.config.annotation.OneToMany;
import java.util.List;
import java.util.ArrayList;
import sqltoy.showcase.sagacity.vo.DictDetailVO;

/**
 * @project sqltoy-showcase
 * @version 1.0.0
 * Table: sag_dict_type,Remark:数据字典分类表   
 */
@Entity(tableName="sag_dict_type",pk_constraint="PRIMARY")
public abstract class AbstractDictTypeVO implements Serializable,
	java.lang.Cloneable {
	 /*--------------- properties string,handier to copy ---------------------*/
	 //full properties 
	 //dictTypeCode,dictTypeName,comments,dataSize,dataType,operator,operateDate,segmentName,segmentDictType,status
	 
	 //not null properties
	 //dictTypeCode,dictTypeName,dataType,status

	/**
	 * 
	 */
	private static final long serialVersionUID = 7767241660976481021L;
	
	/**
	 * 字典类型编码
	 */
	@Id(strategy="assign")
	@Column(name="DICT_TYPE_CODE",length=50L,type=java.sql.Types.VARCHAR,nullable=false)
	protected String dictTypeCode;
	
	/**
	 * 字典类型名称
	 */
	@Column(name="DICT_TYPE_NAME",length=100L,type=java.sql.Types.VARCHAR,nullable=false)
	protected String dictTypeName;
	
	/**
	 * 字典类型描述
	 */
	@Column(name="COMMENTS",length=500L,type=java.sql.Types.VARCHAR,nullable=true)
	protected String comments;
	
	/**
	 * 字典KEY数据长度
	 */
	@Column(name="DATA_SIZE",length=4L,type=java.sql.Types.INTEGER,nullable=true)
	protected Integer dataSize;
	
	/**
	 * 字典KEY数据类型
	 */
	@Column(name="DATA_TYPE",length=1L,defaultValue="0",type=java.sql.Types.INTEGER,nullable=false)
	protected Integer dataType;
	
	/**
	 * 修改人
	 */
	@Column(name="OPERATOR",length=22L,type=java.sql.Types.VARCHAR,nullable=true)
	protected String operator;
	
	/**
	 * 修改日期
	 */
	@Column(name="OPERATE_DATE",length=10L,type=java.sql.Types.DATE,nullable=true)
	protected Date operateDate;
	
	/**
	 * 扩展属性1名称
	 */
	@Column(name="SEGMENT_NAME",length=100L,type=java.sql.Types.VARCHAR,nullable=true)
	protected String segmentName;
	
	/**
	 * 扩展属性来源字典
	 */
	@Column(name="SEGMENT_DICT_TYPE",length=50L,type=java.sql.Types.VARCHAR,nullable=true)
	protected String segmentDictType;
	
	/**
	 * 启用标志
	 */
	@Column(name="STATUS",length=1L,defaultValue="1",type=java.sql.Types.CHAR,nullable=false)
	protected String status;
	

	/**
	 * 主键关联子表信息
	 */
	@OneToMany(fields={"dictTypeCode"},mappedTable="sag_dict_detail",mappedColumns={"DICT_TYPE_CODE"},mappedFields={"dictTypeCode"},load="ENABLED=1",update="delete",delete=false)
	protected List<DictDetailVO> dictDetailVOs=new ArrayList<DictDetailVO>();


	/** default constructor */
	public AbstractDictTypeVO() {
	}
	
	/** pk constructor */
	public AbstractDictTypeVO(String dictTypeCode)
	{
		this.dictTypeCode=dictTypeCode;
	}

	/** minimal constructor */
	public AbstractDictTypeVO(String dictTypeCode,String dictTypeName,Integer dataType,String status)
	{
		this.dictTypeCode=dictTypeCode;
		this.dictTypeName=dictTypeName;
		this.dataType=dataType;
		this.status=status;
	}

	/** full constructor */
	public AbstractDictTypeVO(String dictTypeCode,String dictTypeName,String comments,Integer dataSize,Integer dataType,String operator,Date operateDate,String segmentName,String segmentDictType,String status)
	{
		this.dictTypeCode=dictTypeCode;
		this.dictTypeName=dictTypeName;
		this.comments=comments;
		this.dataSize=dataSize;
		this.dataType=dataType;
		this.operator=operator;
		this.operateDate=operateDate;
		this.segmentName=segmentName;
		this.segmentDictType=segmentDictType;
		this.status=status;
	}
	
	/**
	 *@param dictTypeCode the dictTypeCode to set
	 */
	public void setDictTypeCode(String dictTypeCode) {
		this.dictTypeCode=dictTypeCode;
	}
		
	/**
	 *@return the DictTypeCode
	 */
	public String getDictTypeCode() {
	    return this.dictTypeCode;
	}
	
	/**
	 *@param dictTypeName the dictTypeName to set
	 */
	public void setDictTypeName(String dictTypeName) {
		this.dictTypeName=dictTypeName;
	}
		
	/**
	 *@return the DictTypeName
	 */
	public String getDictTypeName() {
	    return this.dictTypeName;
	}
	
	/**
	 *@param comments the comments to set
	 */
	public void setComments(String comments) {
		this.comments=comments;
	}
		
	/**
	 *@return the Comments
	 */
	public String getComments() {
	    return this.comments;
	}
	
	/**
	 *@param dataSize the dataSize to set
	 */
	public void setDataSize(Integer dataSize) {
		this.dataSize=dataSize;
	}
		
	/**
	 *@return the DataSize
	 */
	public Integer getDataSize() {
	    return this.dataSize;
	}
	
	/**
	 *@param dataType the dataType to set
	 */
	public void setDataType(Integer dataType) {
		this.dataType=dataType;
	}
		
	/**
	 *@return the DataType
	 */
	public Integer getDataType() {
	    return this.dataType;
	}
	
	/**
	 *@param operator the operator to set
	 */
	public void setOperator(String operator) {
		this.operator=operator;
	}
		
	/**
	 *@return the Operator
	 */
	public String getOperator() {
	    return this.operator;
	}
	
	/**
	 *@param operateDate the operateDate to set
	 */
	public void setOperateDate(Date operateDate) {
		this.operateDate=operateDate;
	}
		
	/**
	 *@return the OperateDate
	 */
	public Date getOperateDate() {
	    return this.operateDate;
	}
	
	/**
	 *@param segmentName the segmentName to set
	 */
	public void setSegmentName(String segmentName) {
		this.segmentName=segmentName;
	}
		
	/**
	 *@return the SegmentName
	 */
	public String getSegmentName() {
	    return this.segmentName;
	}
	
	/**
	 *@param segmentDictType the segmentDictType to set
	 */
	public void setSegmentDictType(String segmentDictType) {
		this.segmentDictType=segmentDictType;
	}
		
	/**
	 *@return the SegmentDictType
	 */
	public String getSegmentDictType() {
	    return this.segmentDictType;
	}
	
	/**
	 *@param status the status to set
	 */
	public void setStatus(String status) {
		this.status=status;
	}
		
	/**
	 *@return the Status
	 */
	public String getStatus() {
	    return this.status;
	}


	/**
	 * @return the dictDetailVOs
	 */
	public List<DictDetailVO> getDictDetailVOs() {
		return this.dictDetailVOs;
	}
	
	public void setDictDetailVOs(List<DictDetailVO> dictDetailVOs)	{
		this.dictDetailVOs=dictDetailVOs;
	}

	/**
     * @todo vo columns to String
     */
	public String toString() {
		StringBuilder columnsBuffer=new StringBuilder();
		columnsBuffer.append("dictTypeCode=").append(getDictTypeCode()).append("\n");
		columnsBuffer.append("dictTypeName=").append(getDictTypeName()).append("\n");
		columnsBuffer.append("comments=").append(getComments()).append("\n");
		columnsBuffer.append("dataSize=").append(getDataSize()).append("\n");
		columnsBuffer.append("dataType=").append(getDataType()).append("\n");
		columnsBuffer.append("operator=").append(getOperator()).append("\n");
		columnsBuffer.append("operateDate=").append(getOperateDate()).append("\n");
		columnsBuffer.append("segmentName=").append(getSegmentName()).append("\n");
		columnsBuffer.append("segmentDictType=").append(getSegmentDictType()).append("\n");
		columnsBuffer.append("status=").append(getStatus()).append("\n");
		return columnsBuffer.toString();
	}
}