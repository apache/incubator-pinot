/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/**
 * Autogenerated by Thrift Compiler (0.9.3)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.apache.pinot.common.request;

import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;

import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.annotation.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked"})
public class Literal extends org.apache.thrift.TUnion<Literal, Literal._Fields> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("Literal");
  private static final org.apache.thrift.protocol.TField BOOL_VALUE_FIELD_DESC = new org.apache.thrift.protocol.TField("boolValue", org.apache.thrift.protocol.TType.BOOL, (short)1);
  private static final org.apache.thrift.protocol.TField BYTE_VALUE_FIELD_DESC = new org.apache.thrift.protocol.TField("byteValue", org.apache.thrift.protocol.TType.BYTE, (short)2);
  private static final org.apache.thrift.protocol.TField SHORT_VALUE_FIELD_DESC = new org.apache.thrift.protocol.TField("shortValue", org.apache.thrift.protocol.TType.I16, (short)3);
  private static final org.apache.thrift.protocol.TField INT_VALUE_FIELD_DESC = new org.apache.thrift.protocol.TField("intValue", org.apache.thrift.protocol.TType.I32, (short)4);
  private static final org.apache.thrift.protocol.TField LONG_VALUE_FIELD_DESC = new org.apache.thrift.protocol.TField("longValue", org.apache.thrift.protocol.TType.I64, (short)5);
  private static final org.apache.thrift.protocol.TField DOUBLE_VALUE_FIELD_DESC = new org.apache.thrift.protocol.TField("doubleValue", org.apache.thrift.protocol.TType.DOUBLE, (short)6);
  private static final org.apache.thrift.protocol.TField STRING_VALUE_FIELD_DESC = new org.apache.thrift.protocol.TField("stringValue", org.apache.thrift.protocol.TType.STRING, (short)7);
  private static final org.apache.thrift.protocol.TField BINARY_VALUE_FIELD_DESC = new org.apache.thrift.protocol.TField("binaryValue", org.apache.thrift.protocol.TType.STRING, (short)8);

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    BOOL_VALUE((short)1, "boolValue"),
    BYTE_VALUE((short)2, "byteValue"),
    SHORT_VALUE((short)3, "shortValue"),
    INT_VALUE((short)4, "intValue"),
    LONG_VALUE((short)5, "longValue"),
    DOUBLE_VALUE((short)6, "doubleValue"),
    STRING_VALUE((short)7, "stringValue"),
    BINARY_VALUE((short)8, "binaryValue");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // BOOL_VALUE
          return BOOL_VALUE;
        case 2: // BYTE_VALUE
          return BYTE_VALUE;
        case 3: // SHORT_VALUE
          return SHORT_VALUE;
        case 4: // INT_VALUE
          return INT_VALUE;
        case 5: // LONG_VALUE
          return LONG_VALUE;
        case 6: // DOUBLE_VALUE
          return DOUBLE_VALUE;
        case 7: // STRING_VALUE
          return STRING_VALUE;
        case 8: // BINARY_VALUE
          return BINARY_VALUE;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.BOOL_VALUE, new org.apache.thrift.meta_data.FieldMetaData("boolValue", org.apache.thrift.TFieldRequirementType.OPTIONAL,
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.BOOL)));
    tmpMap.put(_Fields.BYTE_VALUE, new org.apache.thrift.meta_data.FieldMetaData("byteValue", org.apache.thrift.TFieldRequirementType.OPTIONAL,
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.BYTE)));
    tmpMap.put(_Fields.SHORT_VALUE, new org.apache.thrift.meta_data.FieldMetaData("shortValue", org.apache.thrift.TFieldRequirementType.OPTIONAL,
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I16)));
    tmpMap.put(_Fields.INT_VALUE, new org.apache.thrift.meta_data.FieldMetaData("intValue", org.apache.thrift.TFieldRequirementType.OPTIONAL,
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I32)));
    tmpMap.put(_Fields.LONG_VALUE, new org.apache.thrift.meta_data.FieldMetaData("longValue", org.apache.thrift.TFieldRequirementType.OPTIONAL,
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.DOUBLE_VALUE, new org.apache.thrift.meta_data.FieldMetaData("doubleValue", org.apache.thrift.TFieldRequirementType.OPTIONAL,
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.DOUBLE)));
    tmpMap.put(_Fields.STRING_VALUE, new org.apache.thrift.meta_data.FieldMetaData("stringValue", org.apache.thrift.TFieldRequirementType.OPTIONAL,
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.BINARY_VALUE, new org.apache.thrift.meta_data.FieldMetaData("binaryValue", org.apache.thrift.TFieldRequirementType.OPTIONAL,
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING        , true)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(Literal.class, metaDataMap);
  }

  public Literal() {
    super();
  }

  public Literal(_Fields setField, Object value) {
    super(setField, value);
  }

  public Literal(Literal other) {
    super(other);
  }
  public Literal deepCopy() {
    return new Literal(this);
  }

  public static Literal boolValue(boolean value) {
    Literal x = new Literal();
    x.setBoolValue(value);
    return x;
  }

  public static Literal byteValue(byte value) {
    Literal x = new Literal();
    x.setByteValue(value);
    return x;
  }

  public static Literal shortValue(short value) {
    Literal x = new Literal();
    x.setShortValue(value);
    return x;
  }

  public static Literal intValue(int value) {
    Literal x = new Literal();
    x.setIntValue(value);
    return x;
  }

  public static Literal longValue(long value) {
    Literal x = new Literal();
    x.setLongValue(value);
    return x;
  }

  public static Literal doubleValue(double value) {
    Literal x = new Literal();
    x.setDoubleValue(value);
    return x;
  }

  public static Literal stringValue(String value) {
    Literal x = new Literal();
    x.setStringValue(value);
    return x;
  }

  public static Literal binaryValue(ByteBuffer value) {
    Literal x = new Literal();
    x.setBinaryValue(value);
    return x;
  }

  public static Literal binaryValue(byte[] value) {
    Literal x = new Literal();
    x.setBinaryValue(ByteBuffer.wrap(Arrays.copyOf(value, value.length)));
    return x;
  }


  @Override
  protected void checkType(_Fields setField, Object value) throws ClassCastException {
    switch (setField) {
      case BOOL_VALUE:
        if (value instanceof Boolean) {
          break;
        }
        throw new ClassCastException("Was expecting value of type Boolean for field 'boolValue', but got " + value.getClass().getSimpleName());
      case BYTE_VALUE:
        if (value instanceof Byte) {
          break;
        }
        throw new ClassCastException("Was expecting value of type Byte for field 'byteValue', but got " + value.getClass().getSimpleName());
      case SHORT_VALUE:
        if (value instanceof Short) {
          break;
        }
        throw new ClassCastException("Was expecting value of type Short for field 'shortValue', but got " + value.getClass().getSimpleName());
      case INT_VALUE:
        if (value instanceof Integer) {
          break;
        }
        throw new ClassCastException("Was expecting value of type Integer for field 'intValue', but got " + value.getClass().getSimpleName());
      case LONG_VALUE:
        if (value instanceof Long) {
          break;
        }
        throw new ClassCastException("Was expecting value of type Long for field 'longValue', but got " + value.getClass().getSimpleName());
      case DOUBLE_VALUE:
        if (value instanceof Double) {
          break;
        }
        throw new ClassCastException("Was expecting value of type Double for field 'doubleValue', but got " + value.getClass().getSimpleName());
      case STRING_VALUE:
        if (value instanceof String) {
          break;
        }
        throw new ClassCastException("Was expecting value of type String for field 'stringValue', but got " + value.getClass().getSimpleName());
      case BINARY_VALUE:
        if (value instanceof ByteBuffer) {
          break;
        }
        throw new ClassCastException("Was expecting value of type ByteBuffer for field 'binaryValue', but got " + value.getClass().getSimpleName());
      default:
        throw new IllegalArgumentException("Unknown field id " + setField);
    }
  }

  @Override
  protected Object standardSchemeReadValue(org.apache.thrift.protocol.TProtocol iprot, org.apache.thrift.protocol.TField field) throws org.apache.thrift.TException {
    _Fields setField = _Fields.findByThriftId(field.id);
    if (setField != null) {
      switch (setField) {
        case BOOL_VALUE:
          if (field.type == BOOL_VALUE_FIELD_DESC.type) {
            Boolean boolValue;
            boolValue = iprot.readBool();
            return boolValue;
          } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
            return null;
          }
        case BYTE_VALUE:
          if (field.type == BYTE_VALUE_FIELD_DESC.type) {
            Byte byteValue;
            byteValue = iprot.readByte();
            return byteValue;
          } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
            return null;
          }
        case SHORT_VALUE:
          if (field.type == SHORT_VALUE_FIELD_DESC.type) {
            Short shortValue;
            shortValue = iprot.readI16();
            return shortValue;
          } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
            return null;
          }
        case INT_VALUE:
          if (field.type == INT_VALUE_FIELD_DESC.type) {
            Integer intValue;
            intValue = iprot.readI32();
            return intValue;
          } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
            return null;
          }
        case LONG_VALUE:
          if (field.type == LONG_VALUE_FIELD_DESC.type) {
            Long longValue;
            longValue = iprot.readI64();
            return longValue;
          } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
            return null;
          }
        case DOUBLE_VALUE:
          if (field.type == DOUBLE_VALUE_FIELD_DESC.type) {
            Double doubleValue;
            doubleValue = iprot.readDouble();
            return doubleValue;
          } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
            return null;
          }
        case STRING_VALUE:
          if (field.type == STRING_VALUE_FIELD_DESC.type) {
            String stringValue;
            stringValue = iprot.readString();
            return stringValue;
          } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
            return null;
          }
        case BINARY_VALUE:
          if (field.type == BINARY_VALUE_FIELD_DESC.type) {
            ByteBuffer binaryValue;
            binaryValue = iprot.readBinary();
            return binaryValue;
          } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
            return null;
          }
        default:
          throw new IllegalStateException("setField wasn't null, but didn't match any of the case statements!");
      }
    } else {
      org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
      return null;
    }
  }

  @Override
  protected void standardSchemeWriteValue(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    switch (setField_) {
      case BOOL_VALUE:
        Boolean boolValue = (Boolean)value_;
        oprot.writeBool(boolValue);
        return;
      case BYTE_VALUE:
        Byte byteValue = (Byte)value_;
        oprot.writeByte(byteValue);
        return;
      case SHORT_VALUE:
        Short shortValue = (Short)value_;
        oprot.writeI16(shortValue);
        return;
      case INT_VALUE:
        Integer intValue = (Integer)value_;
        oprot.writeI32(intValue);
        return;
      case LONG_VALUE:
        Long longValue = (Long)value_;
        oprot.writeI64(longValue);
        return;
      case DOUBLE_VALUE:
        Double doubleValue = (Double)value_;
        oprot.writeDouble(doubleValue);
        return;
      case STRING_VALUE:
        String stringValue = (String)value_;
        oprot.writeString(stringValue);
        return;
      case BINARY_VALUE:
        ByteBuffer binaryValue = (ByteBuffer)value_;
        oprot.writeBinary(binaryValue);
        return;
      default:
        throw new IllegalStateException("Cannot write union with unknown field " + setField_);
    }
  }

  @Override
  protected Object tupleSchemeReadValue(org.apache.thrift.protocol.TProtocol iprot, short fieldID) throws org.apache.thrift.TException {
    _Fields setField = _Fields.findByThriftId(fieldID);
    if (setField != null) {
      switch (setField) {
        case BOOL_VALUE:
          Boolean boolValue;
          boolValue = iprot.readBool();
          return boolValue;
        case BYTE_VALUE:
          Byte byteValue;
          byteValue = iprot.readByte();
          return byteValue;
        case SHORT_VALUE:
          Short shortValue;
          shortValue = iprot.readI16();
          return shortValue;
        case INT_VALUE:
          Integer intValue;
          intValue = iprot.readI32();
          return intValue;
        case LONG_VALUE:
          Long longValue;
          longValue = iprot.readI64();
          return longValue;
        case DOUBLE_VALUE:
          Double doubleValue;
          doubleValue = iprot.readDouble();
          return doubleValue;
        case STRING_VALUE:
          String stringValue;
          stringValue = iprot.readString();
          return stringValue;
        case BINARY_VALUE:
          ByteBuffer binaryValue;
          binaryValue = iprot.readBinary();
          return binaryValue;
        default:
          throw new IllegalStateException("setField wasn't null, but didn't match any of the case statements!");
      }
    } else {
      throw new TProtocolException("Couldn't find a field with field id " + fieldID);
    }
  }

  @Override
  protected void tupleSchemeWriteValue(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    switch (setField_) {
      case BOOL_VALUE:
        Boolean boolValue = (Boolean)value_;
        oprot.writeBool(boolValue);
        return;
      case BYTE_VALUE:
        Byte byteValue = (Byte)value_;
        oprot.writeByte(byteValue);
        return;
      case SHORT_VALUE:
        Short shortValue = (Short)value_;
        oprot.writeI16(shortValue);
        return;
      case INT_VALUE:
        Integer intValue = (Integer)value_;
        oprot.writeI32(intValue);
        return;
      case LONG_VALUE:
        Long longValue = (Long)value_;
        oprot.writeI64(longValue);
        return;
      case DOUBLE_VALUE:
        Double doubleValue = (Double)value_;
        oprot.writeDouble(doubleValue);
        return;
      case STRING_VALUE:
        String stringValue = (String)value_;
        oprot.writeString(stringValue);
        return;
      case BINARY_VALUE:
        ByteBuffer binaryValue = (ByteBuffer)value_;
        oprot.writeBinary(binaryValue);
        return;
      default:
        throw new IllegalStateException("Cannot write union with unknown field " + setField_);
    }
  }

  @Override
  protected org.apache.thrift.protocol.TField getFieldDesc(_Fields setField) {
    switch (setField) {
      case BOOL_VALUE:
        return BOOL_VALUE_FIELD_DESC;
      case BYTE_VALUE:
        return BYTE_VALUE_FIELD_DESC;
      case SHORT_VALUE:
        return SHORT_VALUE_FIELD_DESC;
      case INT_VALUE:
        return INT_VALUE_FIELD_DESC;
      case LONG_VALUE:
        return LONG_VALUE_FIELD_DESC;
      case DOUBLE_VALUE:
        return DOUBLE_VALUE_FIELD_DESC;
      case STRING_VALUE:
        return STRING_VALUE_FIELD_DESC;
      case BINARY_VALUE:
        return BINARY_VALUE_FIELD_DESC;
      default:
        throw new IllegalArgumentException("Unknown field id " + setField);
    }
  }

  @Override
  protected org.apache.thrift.protocol.TStruct getStructDesc() {
    return STRUCT_DESC;
  }

  @Override
  protected _Fields enumForId(short id) {
    return _Fields.findByThriftIdOrThrow(id);
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }


  public boolean getBoolValue() {
    if (getSetField() == _Fields.BOOL_VALUE) {
      return (Boolean)getFieldValue();
    } else {
      throw new RuntimeException("Cannot get field 'boolValue' because union is currently set to " + getFieldDesc(getSetField()).name);
    }
  }

  public void setBoolValue(boolean value) {
    setField_ = _Fields.BOOL_VALUE;
    value_ = value;
  }

  public byte getByteValue() {
    if (getSetField() == _Fields.BYTE_VALUE) {
      return (Byte)getFieldValue();
    } else {
      throw new RuntimeException("Cannot get field 'byteValue' because union is currently set to " + getFieldDesc(getSetField()).name);
    }
  }

  public void setByteValue(byte value) {
    setField_ = _Fields.BYTE_VALUE;
    value_ = value;
  }

  public short getShortValue() {
    if (getSetField() == _Fields.SHORT_VALUE) {
      return (Short)getFieldValue();
    } else {
      throw new RuntimeException("Cannot get field 'shortValue' because union is currently set to " + getFieldDesc(getSetField()).name);
    }
  }

  public void setShortValue(short value) {
    setField_ = _Fields.SHORT_VALUE;
    value_ = value;
  }

  public int getIntValue() {
    if (getSetField() == _Fields.INT_VALUE) {
      return (Integer)getFieldValue();
    } else {
      throw new RuntimeException("Cannot get field 'intValue' because union is currently set to " + getFieldDesc(getSetField()).name);
    }
  }

  public void setIntValue(int value) {
    setField_ = _Fields.INT_VALUE;
    value_ = value;
  }

  public long getLongValue() {
    if (getSetField() == _Fields.LONG_VALUE) {
      return (Long)getFieldValue();
    } else {
      throw new RuntimeException("Cannot get field 'longValue' because union is currently set to " + getFieldDesc(getSetField()).name);
    }
  }

  public void setLongValue(long value) {
    setField_ = _Fields.LONG_VALUE;
    value_ = value;
  }

  public double getDoubleValue() {
    if (getSetField() == _Fields.DOUBLE_VALUE) {
      return (Double)getFieldValue();
    } else {
      throw new RuntimeException("Cannot get field 'doubleValue' because union is currently set to " + getFieldDesc(getSetField()).name);
    }
  }

  public void setDoubleValue(double value) {
    setField_ = _Fields.DOUBLE_VALUE;
    value_ = value;
  }

  public String getStringValue() {
    if (getSetField() == _Fields.STRING_VALUE) {
      return (String)getFieldValue();
    } else {
      throw new RuntimeException("Cannot get field 'stringValue' because union is currently set to " + getFieldDesc(getSetField()).name);
    }
  }

  public void setStringValue(String value) {
    if (value == null) throw new NullPointerException();
    setField_ = _Fields.STRING_VALUE;
    value_ = value;
  }

  public byte[] getBinaryValue() {
    setBinaryValue(org.apache.thrift.TBaseHelper.rightSize(bufferForBinaryValue()));
    ByteBuffer b = bufferForBinaryValue();
    return b == null ? null : b.array();
  }

  public ByteBuffer bufferForBinaryValue() {
    if (getSetField() == _Fields.BINARY_VALUE) {
      return org.apache.thrift.TBaseHelper.copyBinary((ByteBuffer)getFieldValue());
    } else {
      throw new RuntimeException("Cannot get field 'binaryValue' because union is currently set to " + getFieldDesc(getSetField()).name);
    }
  }

  public void setBinaryValue(byte[] value) {
    setBinaryValue(ByteBuffer.wrap(Arrays.copyOf(value, value.length)));
  }

  public void setBinaryValue(ByteBuffer value) {
    if (value == null) throw new NullPointerException();
    setField_ = _Fields.BINARY_VALUE;
    value_ = value;
  }

  public boolean isSetBoolValue() {
    return setField_ == _Fields.BOOL_VALUE;
  }


  public boolean isSetByteValue() {
    return setField_ == _Fields.BYTE_VALUE;
  }


  public boolean isSetShortValue() {
    return setField_ == _Fields.SHORT_VALUE;
  }


  public boolean isSetIntValue() {
    return setField_ == _Fields.INT_VALUE;
  }


  public boolean isSetLongValue() {
    return setField_ == _Fields.LONG_VALUE;
  }


  public boolean isSetDoubleValue() {
    return setField_ == _Fields.DOUBLE_VALUE;
  }


  public boolean isSetStringValue() {
    return setField_ == _Fields.STRING_VALUE;
  }


  public boolean isSetBinaryValue() {
    return setField_ == _Fields.BINARY_VALUE;
  }


  public boolean equals(Object other) {
    if (other instanceof Literal) {
      return equals((Literal)other);
    } else {
      return false;
    }
  }

  public boolean equals(Literal other) {
    return other != null && getSetField() == other.getSetField() && getFieldValue().equals(other.getFieldValue());
  }

  @Override
  public int compareTo(Literal other) {
    int lastComparison = org.apache.thrift.TBaseHelper.compareTo(getSetField(), other.getSetField());
    if (lastComparison == 0) {
      return org.apache.thrift.TBaseHelper.compareTo(getFieldValue(), other.getFieldValue());
    }
    return lastComparison;
  }


  @Override
  public int hashCode() {
    List<Object> list = new ArrayList<Object>();
    list.add(this.getClass().getName());
    org.apache.thrift.TFieldIdEnum setField = getSetField();
    if (setField != null) {
      list.add(setField.getThriftFieldId());
      Object value = getFieldValue();
      if (value instanceof org.apache.thrift.TEnum) {
        list.add(((org.apache.thrift.TEnum)getFieldValue()).getValue());
      } else {
        list.add(value);
      }
    }
    return list.hashCode();
  }
  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }


  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }


}
