/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.server.types.mcompat.mtypes;

import com.foundationdb.server.error.InvalidDateFormatException;
import com.foundationdb.server.error.InvalidParameterValueException;
import com.foundationdb.server.types.TBundleID;
import com.foundationdb.server.types.TClass;
import com.foundationdb.server.types.TClassFormatter;
import com.foundationdb.server.types.TExecutionContext;
import com.foundationdb.server.types.mcompat.MParsers;
import com.foundationdb.server.types.TInstance;
import com.foundationdb.server.types.aksql.AkCategory;
import com.foundationdb.server.types.common.types.NoAttrTClass;
import com.foundationdb.server.types.mcompat.MBundle;
import com.foundationdb.server.types.mcompat.mcasts.CastUtils;
import com.foundationdb.server.types.value.UnderlyingType;
import com.foundationdb.server.types.value.ValueSource;
import java.text.DateFormatSymbols;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.foundationdb.sql.types.TypeId;
import com.foundationdb.util.AkibanAppender;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.MutableDateTime;
import org.joda.time.base.BaseDateTime;

public class MDatetimes
{
    public static final int MAX_YEAR = 2099;
    private static final TBundleID MBundleID = MBundle.INSTANCE.id();

    // TODO: The serialization size of these old Type instances saved a byte.  Can
    // remove this if we are willing to consume that extra byte and render existing
    // volumes unreadable. (Changing the serializationSize field to 3 would
    // incompatibly change cost estimates.)
    static class DTMediumInt extends NoAttrTClass {
        public DTMediumInt(TBundleID bundle, String name, Enum<?> category, TClassFormatter formatter, int internalRepVersion, int serializationVersion, int serializationSize, UnderlyingType underlyingType, com.foundationdb.server.types.TParser parser, int defaultVarcharLen, TypeId typeId) {
            super(bundle, name, category, formatter, internalRepVersion, serializationVersion, serializationSize, underlyingType, parser, defaultVarcharLen, typeId);
        }

        @Override
        public int fixedSerializationSize(TInstance type) {
            assert (4 == super.fixedSerializationSize(type));
            return 3;
        }
    }

    public static final NoAttrTClass DATE = new DTMediumInt(MBundleID,
            "date", AkCategory.DATE_TIME, FORMAT.DATE, 1, 1, 4, UnderlyingType.INT_32, MParsers.DATE, 10, TypeId.DATE_ID)
    {
        public TClass widestComparable()
        {
            return DATETIME;
        }
    };
    public static final NoAttrTClass DATETIME = new NoAttrTClass(MBundleID,
            "datetime", AkCategory.DATE_TIME, FORMAT.DATETIME,  1, 1, 8, UnderlyingType.INT_64, MParsers.DATETIME, 19, TypeId.DATETIME_ID);
    public static final NoAttrTClass TIME = new DTMediumInt(MBundleID,
            "time", AkCategory.DATE_TIME, FORMAT.TIME, 1, 1, 4, UnderlyingType.INT_32, MParsers.TIME, 8, TypeId.TIME_ID);
    public static final NoAttrTClass YEAR = new NoAttrTClass(MBundleID,
            "year", AkCategory.DATE_TIME, FORMAT.YEAR, 1, 1, 1, UnderlyingType.INT_16, MParsers.YEAR, 4, TypeId.YEAR_ID);
    public static final NoAttrTClass TIMESTAMP = new NoAttrTClass(MBundleID,
            "timestamp", AkCategory.DATE_TIME, FORMAT.TIMESTAMP, 1, 1, 4, UnderlyingType.INT_32, MParsers.TIMESTAMP, 19, TypeId.TIMESTAMP_ID);

    public static final List<String> SUPPORTED_LOCALES = new LinkedList<>();
    
    public static final Map<String, String[]> MONTHS;
    public static final Map<String, String[]> SHORT_MONTHS;
    
    public static final Map<String, String[]> WEEKDAYS;
    public static final Map<String, String[]> SHORT_WEEKDAYS;

    
    public static enum FORMAT implements TClassFormatter {
        DATE {
            @Override
            public void format(TInstance type, ValueSource source, AkibanAppender out) {
                out.append(dateToString(source.getInt32()));
            }

            @Override
            public void formatAsLiteral(TInstance type, ValueSource source, AkibanAppender out) {
                out.append("DATE '");
                out.append(dateToString(source.getInt32()));
                out.append("'");
            }
        }, 
        DATETIME {
            @Override
            public void format(TInstance type, ValueSource source, AkibanAppender out) {
                out.append(datetimeToString(source.getInt64()));
            }

            @Override
            public void formatAsLiteral(TInstance type, ValueSource source, AkibanAppender out) {
                out.append("TIMESTAMP '");
                out.append(datetimeToString(source.getInt64()));
                out.append("'");
            }
        }, 
        TIME {
            @Override
            public void format(TInstance type, ValueSource source, AkibanAppender out) {
                out.append(timeToString(source.getInt32()));
            }

            @Override
            public void formatAsLiteral(TInstance type, ValueSource source, AkibanAppender out) {
                out.append("TIME '");
                out.append(timeToString(source.getInt32()));
                out.append("'");
            }
        }, 
        YEAR {         
            @Override
            public void format(TInstance type, ValueSource source, AkibanAppender out)
            {
                short raw = source.getInt16();
                if (raw == 0)
                    out.append("0000");
                else
                    out.append(raw + 1900);
            }

            @Override
            public void formatAsLiteral(TInstance type, ValueSource source, AkibanAppender out) {
                format(type, source, out);
            }
        }, 
        TIMESTAMP {
            @Override
            public void format(TInstance type, ValueSource source, AkibanAppender out) {
                out.append(timestampToString(source.getInt32(), null));
            }

            @Override
            public void formatAsLiteral(TInstance type, ValueSource source, AkibanAppender out) {
                out.append("TIMESTAMP '");
                out.append(timestampToString(source.getInt32(), null));
                out.append("'");
            }
        };

        @Override
        public void formatAsJson(TInstance type, ValueSource source, AkibanAppender out) {
            out.append('"');
            format(type, source, out);
            out.append('"');
        }
    }
    
    static
    {
        // TODO: add all supported LOCALES here
        SUPPORTED_LOCALES.add(Locale.ENGLISH.getLanguage());
        
       Map<String, String[]> months = new HashMap<>();
       Map<String, String[]> shortMonths = new HashMap<>();
       Map<String, String[]>weekDays = new HashMap<>();
       Map<String, String[]>shortWeekdays = new HashMap<>();

       for (String locale : SUPPORTED_LOCALES)
       {
           DateFormatSymbols fm = new DateFormatSymbols(new Locale(locale));
           
           months.put(locale, fm.getMonths());
           shortMonths.put(locale, fm.getShortMonths());
           
           weekDays.put(locale, fm.getWeekdays());
           shortWeekdays.put(locale, fm.getShortWeekdays());
       }
       
       MONTHS = Collections.unmodifiableMap(months);
       SHORT_MONTHS = Collections.unmodifiableMap(shortMonths);
       WEEKDAYS = Collections.unmodifiableMap(weekDays);
       SHORT_WEEKDAYS = Collections.unmodifiableMap(shortWeekdays);
    }

    public static String getMonthName(int numericRep, String locale, TExecutionContext context)
    {
        return getVal(numericRep - 1, locale, context, MONTHS, "month", 11, 0);
    }
    
    public static String getShortMonthName(int numericRep, String locale, TExecutionContext context)
    {
        return getVal(numericRep -1, locale, context, SHORT_MONTHS, "month", 11, 0);
    }
    
    public static String getWeekDayName(int numericRep, String locale, TExecutionContext context)
    {
        return getVal(numericRep, locale, context, WEEKDAYS, "weekday", 6, 0);
    }
    
    public static String getShortWeekDayName(int numericRep, String locale, TExecutionContext context)
    {
        return getVal(numericRep, locale, context, SHORT_WEEKDAYS, "weekdays", 6, 0);
    }
    
    static String getVal (int numericRep, 
                          String locale,
                          TExecutionContext context,
                          Map<String, String[]> map,
                          String name,
                          int max, int min)
    {
        if (numericRep > max || numericRep < min)
        {
            context.reportBadValue(name + " out of range: " + numericRep);
            return null;
        }
        
        String ret[] = map.get(locale);
        if (ret == null)
        {
            context.reportBadValue("Unsupported locale: " + locale);
            return null;
        }
        
        return ret[numericRep];
    }
    
    public static long[] fromJodaDatetime (MutableDateTime date)
    {
        return new long[]
        {
            date.getYear(),
            date.getMonthOfYear(),
            date.getDayOfMonth(),
            date.getHourOfDay(),
            date.getMinuteOfHour(),
            date.getSecondOfMinute()
        };
    }
    
    public static long[] fromJodaDatetime (DateTime date)
    {
        return new long[]
        {
            date.getYear(),
            date.getMonthOfYear(),
            date.getDayOfMonth(),
            date.getHourOfDay(),
            date.getMinuteOfHour(),
            date.getSecondOfMinute()
        };
    }

    public static MutableDateTime toJodaDatetime(long ymd_hms[], String tz)
    {
        return new MutableDateTime((int)ymd_hms[YEAR_INDEX], (int)ymd_hms[MONTH_INDEX], (int)ymd_hms[DAY_INDEX],
                                   (int)ymd_hms[HOUR_INDEX], (int)ymd_hms[MIN_INDEX], (int)ymd_hms[SEC_INDEX], 0,
                                   DateTimeZone.forID(tz));
    }

    public static String dateToString (int date)
    {
        int yr = date / 512;
        int m = date / 32 % 16;
        int d = date % 32;
        
        return String.format("%04d-%02d-%02d", yr, m, d);
    }

    public static int parseYear (String st, TExecutionContext context) 
    {
        try{
            int value = Integer.parseInt(st);
            return value == 0 ? 0 : (value - 1900);
        } catch (NumberFormatException ex) {
            throw new InvalidDateFormatException ("year", st);
        }
    }
    
    public static int parseDate(String st, TExecutionContext context)
    {
        String tks[];
        
        // date and time tokens
        String datetime[] = st.split(" ");
        if (datetime.length == 2)
            tks = datetime[0].split("-"); // ignore the time part
        else
            tks = st.split("-");
        
        try
        {
            int year, month, day;
            if (tks.length == 3)
            {
                year = Integer.parseInt(tks[0]);
                month = Integer.parseInt(tks[1]);
                day = (int) CastUtils.parseInRange(tks[2], Long.MAX_VALUE, Long.MIN_VALUE, context);
            }
            else if (tks.length == 1)
            {
                long[] ymd = fromDate(Long.parseLong(tks[0]));
                year = (int)ymd[YEAR_INDEX];
                month = (int)ymd[MONTH_INDEX];
                day = (int)ymd[DAY_INDEX];
            }
            else
                throw new InvalidDateFormatException("date", st);

            if (!isValidDayMonth(year, month, day))
                throw new InvalidDateFormatException("date", st);
            else
                return year * 512
                    + month * 32
                    + day;
        }
        catch (NumberFormatException ex)
        {
            throw new InvalidDateFormatException("date", st);
        }
    }

    /**
     * TODO: This function is ised in CUR_DATE/TIME, could speed up the performance
     * by directly passing the Date(Time) object to this function
     * so it won't have to create one.
     * 
     * @param millis
     * @param tz
     * @return the (MySQL) encoded DATE value
     */
    public static int encodeDate(long millis, String tz)
    {
        DateTime dt = new DateTime(millis, DateTimeZone.forID(tz));
        
        return dt.getYear() * 512
                + dt.getMonthOfYear() * 32
                + dt.getDayOfMonth();
    }

    public static long[] decodeDate(long val)
    {
        return new long[]
        {
            val / 512,
            val / 32 % 16,
            val % 32,
            0,
            0,
            0
        };
    }
    
    public static int encodeDate (long ymd[])
    {
        return (int)(ymd[YEAR_INDEX] * 512 + ymd[MONTH_INDEX] * 32 + ymd[DAY_INDEX]);
    }
    
    public static long[] fromDate(long val)
    {
        return new long[]
        {
            val / DATE_YEAR,
            val / DATE_MONTH % DATE_MONTH,
            val % DATE_MONTH,
            0,
            0,
            0
        };
    }
    
    public static String datetimeToString(long datetime)
    {
        long dt[] = decodeDatetime(datetime);
        
        return String.format("%04d-%02d-%02d %02d:%02d:%02d",
                             dt[YEAR_INDEX],
                             dt[MONTH_INDEX],
                             dt[DAY_INDEX],
                             dt[HOUR_INDEX],
                             dt[MIN_INDEX],
                             dt[SEC_INDEX]);
    }
    
    /**
     * parse the string for DATE, DATETIME or TIME and store the parsed values in ymd
     * @return a integer indicating the type that's been parsed:
     *      DATE_ST :       date string
     *      TIME_ST:        time string
     *      DATETIME_ST:    datetime string
     */
    public static StringType parseDateOrTime(String st, long ymd[])
    {        
        st = st.trim();

        Matcher datetime;
        Matcher time;
        Matcher timeNoday;
        
        String year = "0";
        String month = "0";
        String day = "0";
        String hour = "0";
        String minute = "0";
        String seconds = "0";
        
        datetime = DATE_PATTERN.matcher(st.trim());
        if (datetime.matches())
        {
            StringType ret = StringType.DATE_ST;
            year = datetime.group(DATE_YEAR_GROUP);
            month = datetime.group(DATE_MONTH_GROUP);
            day = datetime.group(DATE_DAY_GROUP);
            
            if (datetime.group(TIME_GROUP) != null)
            {
                ret = StringType.DATETIME_ST;
                hour = datetime.group(TIME_HOUR_GROUP);
                minute = datetime.group(TIME_MINUTE_GROUP);
                seconds = datetime.group(TIME_SECOND_GROUP);
            }
            
            
            
            if (doParse(ymd,
                        year, month, day,
                        hour, minute, seconds) > 0
                && isValidDatetime(ymd))
                return ret;
            else
                return ret == StringType.DATETIME_ST
                        ? StringType.INVALID_DATETIME_ST
                        : StringType.INVALID_DATE_ST;
        }
        else if ((time = TIME_WITH_DAY_PATTERN.matcher(st)).matches())
        {   
            day = time.group(MDatetimes.TIME_WITH_DAY_DAY_GROUP);
            hour = time.group(MDatetimes.TIME_WITH_DAY_HOUR_GROUP);
            minute = time.group(MDatetimes.TIME_WITH_DAY_MIN_GROUP);
            seconds = time.group(MDatetimes.TIME_WITH_DAY_SEC_GROUP);

            if (doParse(ymd,
                        year, month, day,
                        hour, minute, seconds) > 0
                && MDatetimes.isValidHrMinSec(ymd, false))
            {
                // adjust DAY to HOUR 
                int sign = 1;
                if (ymd[DAY_INDEX] < 0)
                    ymd[DAY_INDEX] *= (sign = -1);
                ymd[HOUR_INDEX] = sign * (ymd[HOUR_INDEX] += ymd[DAY_INDEX] * 24);
                ymd[DAY_INDEX] = 0;
                return StringType.TIME_ST;
            }
            else
                return StringType.INVALID_TIME_ST;
        }
        else if ((timeNoday = TIME_WITHOUT_DAY_PATTERN.matcher(st)).matches())
        {
            hour = timeNoday.group(MDatetimes.TIME_WITHOUT_DAY_HOUR_GROUP);
            minute = timeNoday.group(MDatetimes.TIME_WITHOUT_DAY_MIN_GROUP);
            seconds = timeNoday.group(MDatetimes.TIME_WITHOUT_DAY_SEC_GROUP);

            if (doParse(ymd,
                        year, month, day,
                        hour, minute, seconds) > 0
                && MDatetimes.isValidHrMinSec(ymd, false))
                return StringType.TIME_ST;
            else
                return StringType.INVALID_TIME_ST;
        }
        else // last attemp, split by any non-alphanumeric and assume the string is a DATE_STR
        {
            String parts[] = st.split("\\s++");
            String dateTk[];
            String timeTk[];
            switch(parts.length)
            {
                case 2:
                    if ((dateTk = parts[0].split(st)).length != 3
                            || (timeTk = parts[1].split(DELIM)).length != 3)
                        break;

                    if (doParse(ymd,
                        year, month, day,
                        hour, minute, seconds) > 0
                        && isValidDatetime(ymd))
                        return StringType.DATETIME_ST;
                    else
                        return StringType.INVALID_DATETIME_ST;
                case 1:
                    if ((dateTk = parts[0].split(DELIM)).length != 3)
                        break;
                    doParse(st,
                            ymd,
                            dateTk[0], dateTk[1], dateTk[2]);
                    if (isValidDayMonth(ymd))
                        return StringType.DATE_ST;
                    else
                        return StringType.INVALID_DATE_ST;
            }
        }

        // anything else is an error (impossible to parse!)
        throw new InvalidDateFormatException("datetime", st);
    }

    private static long adjustYear(long year)
    {
        if (year >= 10 && year <= 69)
            return year + 2000;
        else if (year >= 70 && year <= 99)
            return year + 1900;
        else
            return year;
    }
    
    private static void doParse(String st, // for error message only
                                long ymd[],
                                String year, String month, String day)
    {
        try
        {
            ymd[YEAR_INDEX] = adjustYear(Long.parseLong(year.trim()));
            ymd[MONTH_INDEX] = Long.parseLong(month.trim());
            ymd[DAY_INDEX] = Long.parseLong(day.trim());
            ymd[HOUR_INDEX] = 0;
            ymd[MIN_INDEX] = 0;
            ymd[SEC_INDEX] = 0;

        }
        catch (NumberFormatException ex)
        {
            throw new InvalidDateFormatException("datetime", st);
        }
    }

    
    private static int doParse(long ymd[],
                                String year, String month, String day,
                                String hour, String minute, String seconds)
    {
        try
        {
            ymd[YEAR_INDEX] = adjustYear(Long.parseLong(year.trim()));
            ymd[MONTH_INDEX] = Long.parseLong(month.trim());
            ymd[DAY_INDEX] = Long.parseLong(day.trim());
            ymd[HOUR_INDEX] = Long.parseLong(hour.trim());
            ymd[MIN_INDEX] = Long.parseLong(minute.trim());
            ymd[SEC_INDEX] = Long.parseLong(seconds.trim());
            return 1;

        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    //TODO: Any way of extracting the common code from ExtractorsForDateTime?
    public static long parseDatetime(String st)
    {
        Matcher m = DATE_PATTERN.matcher(st.trim());

            if (!m.matches() || m.group(DATE_GROUP) == null) 
            throw new InvalidDateFormatException("datetime", st);

        String year = m.group(DATE_YEAR_GROUP);
        String month = m.group(DATE_MONTH_GROUP);
        String day = m.group(DATE_DAY_GROUP);
        String hour = "0";
        String minute = "0";
        String seconds = "0";
        
        if (m.group(TIME_GROUP) != null)
        {
            hour = m.group(TIME_HOUR_GROUP);
            minute = m.group(TIME_MINUTE_GROUP);
            seconds = m.group(TIME_SECOND_GROUP);
        }

        try
        {
            long ret[] = new long[]
            {
                Long.parseLong(year),
                Long.parseLong(month),
                Long.parseLong(day),
                Long.parseLong(hour),
                Long.parseLong(minute),
                Long.parseLong(seconds)
            };
            
            if (!isValidDatetime(ret))
                throw new InvalidDateFormatException("datetime", st);
            else
                return ret[0] * DATETIME_YEAR_SCALE
                       + ret[1] * DATETIME_MONTH_SCALE
                       + ret[2] * DATETIME_DAY_SCALE
                       + ret[3] * DATETIME_HOUR_SCALE
                       + ret[4] * DATETIME_MIN_SCALE
                       + ret[5];
        }
        catch (NumberFormatException ex)
        {
            throw new InvalidDateFormatException("datetime", st);
        }
    }
    
    public static long encodeDatetime(BaseDateTime dt)
    {
        return dt.getYear() * DATETIME_YEAR_SCALE
                + dt.getMonthOfYear() * DATETIME_MONTH_SCALE
                + dt.getDayOfMonth() * DATETIME_DAY_SCALE
                + dt.getHourOfDay() * DATETIME_HOUR_SCALE
                + dt.getMinuteOfHour() * DATETIME_MIN_SCALE
                + dt.getSecondOfMinute();
    }
    
    /**
     * TODO: Same as encodeDate(long, String)'s
     * 
     * @param millis number of millis second from UTC in the specified timezone
     * @param tz
     * @return the (MySQL) encoded DATETIME value
     */
    public static long encodeDatetime(long millis, String tz)
    {
        DateTime dt = new DateTime(millis, DateTimeZone.forID(tz));
        
        return dt.getYear() * DATETIME_YEAR_SCALE
                + dt.getMonthOfYear() * DATETIME_MONTH_SCALE
                + dt.getDayOfMonth() * DATETIME_DAY_SCALE
                + dt.getHourOfDay() * DATETIME_HOUR_SCALE
                + dt.getMinuteOfHour() * DATETIME_MIN_SCALE
                + dt.getSecondOfMinute();
    }
        
    public static long encodeDatetime(long ymdHMS[])
    {
        return ymdHMS[YEAR_INDEX] * DATETIME_YEAR_SCALE
                + ymdHMS[MONTH_INDEX] * DATETIME_MONTH_SCALE
                + ymdHMS[DAY_INDEX] * DATETIME_DAY_SCALE
                + ymdHMS[HOUR_INDEX] * DATETIME_HOUR_SCALE
                + ymdHMS[MIN_INDEX] * DATETIME_MIN_SCALE
                + ymdHMS[SEC_INDEX];
    }

    public static long[] decodeDatetime (long val)
    {
        return new long[]
        {
            val / DATETIME_YEAR_SCALE,
            val / DATETIME_MONTH_SCALE % 100,
            val / DATETIME_DAY_SCALE % 100,
            val / DATETIME_HOUR_SCALE % 100,
            val / DATETIME_MIN_SCALE % 100,
            val % 100
        };
    }
    
    public static String timeToString(int val)
    {
        String sign = "";
        if (val < 0) {
            val = - val;
            sign = "-";
        }
        int h  = (int)(val / DATETIME_HOUR_SCALE);
        int m = (int)(val / DATETIME_MIN_SCALE) % 100;
        int s = (int)val % 100;

        return String.format("%s%d:%02d:%02d", sign, h, m, s);
    }

    public static void timeToDatetime(long time[])
    {
        time[YEAR_INDEX] = adjustYear(time[HOUR_INDEX]);
        time[MONTH_INDEX] = time[MIN_INDEX];
        time[DAY_INDEX] = time[SEC_INDEX];
        
        // erase the time portion
        time[HOUR_INDEX] = 0;
        time[MIN_INDEX] = 0;
        time[SEC_INDEX] = 0;
        
        return;
    }
    
    public static int parseTime (String string, TExecutionContext context)
    {
          // (-)HH:MM:SS
        int mul = 1;
        int hours = 0;
        int minutes = 0;
        int seconds = 0;
        int offset = 0;
        boolean shortTime = false;
        if (string.length() > 0 && string.charAt(0) == '-')
        {
            mul = -1;
            string = string.substring(1);
        }

        hhmmss:
        {
            if (string.length() > 8 )
            {
                Matcher timeNoday = TIME_WITHOUT_DAY_PATTERN.matcher(string);
                if (timeNoday.matches()) {
                    try {
                        hours = Integer.parseInt(timeNoday.group(MDatetimes.TIME_WITHOUT_DAY_HOUR_GROUP));
                        minutes = Integer.parseInt(timeNoday.group(MDatetimes.TIME_WITHOUT_DAY_MIN_GROUP));
                        seconds = Integer.parseInt(timeNoday.group(MDatetimes.TIME_WITHOUT_DAY_SEC_GROUP));
                        break hhmmss;
                    }
                    catch (NumberFormatException ex)
                    {
                        throw new InvalidDateFormatException("time", string);
                    }
                }

                String parts[] = string.split(" ");

                // just get the TIME part
                if (parts.length == 2)
                {
                    String datePts[] = parts[0].split("-");
                    try
                    {
                        switch (datePts.length)
                        {
                            case 1: // <some value> hh:mm:ss ==> make sure <some value> is a numeric value
                                hours = Integer.parseInt(datePts[0]) * 24;
                                break;
                            case 3: // YYYY-MM-dd hh:mm:ss
                                shortTime = true;
                                if (isValidDayMonth(Integer.parseInt(datePts[0]),
                                                    Integer.parseInt(datePts[1]),
                                                    Integer.parseInt(datePts[2])))
                                    break;
                                // fall thru
                            default:
                                throw new InvalidDateFormatException("time", string);
                        }
                    }
                    catch (NumberFormatException ex)
                    {
                        throw new InvalidDateFormatException("time", string);
                    }

                    string = parts[1];
                }
            }

            final String values[] = string.split(":");

            try
            {
                if (values.length == 1) 
                {
                    long[] hms = decodeTime(Long.parseLong(values[offset]));
                    hours += hms[HOUR_INDEX];
                    minutes = (int)hms[MIN_INDEX];
                    seconds = (int)hms[SEC_INDEX];
                }
                else 
                {
                    switch (values.length)
                    {
                    case 3:
                        hours += Integer.parseInt(values[offset++]); // fall
                    case 2:
                        minutes = Integer.parseInt(values[offset++]); // fall
                    case 1:
                        seconds = Integer.parseInt(values[offset]);
                        break;
                    default:
                        throw new InvalidDateFormatException("time", string);
                    }

                    minutes += seconds / 60;
                    seconds %= 60;
                    hours += minutes / 60;
                    minutes %= 60;
                }
            }
            catch (NumberFormatException ex)
            {
                throw new InvalidDateFormatException("time", string);
            }
        }

        if (!isValidHrMinSec(hours, minutes, seconds, shortTime))
            throw new InvalidDateFormatException("time", string);
        
        long ret = mul * (hours* DATETIME_HOUR_SCALE + minutes* DATETIME_MIN_SCALE + seconds);
        
        return (int)CastUtils.getInRange(TIME_MAX, TIME_MIN, ret, context);
    }
    public static long[] decodeTime(long val)
    {
        int sign;
        if (val < 0)
            val *= sign = -1;
        else
            sign = 1;
        
         long ret[] =  new long[]
         {
            1970,
            1,
            1,
            val / DATETIME_HOUR_SCALE,
            val / DATETIME_MIN_SCALE % 100,
            val % 100
         };
   
         if (sign < 0)
         {
             int ind = HOUR_INDEX;
             // find the first element that is NOT zero
             while (ret[ind] == 0 && ind < ret.length)
                ++ind; 

             // and place the sign on it!
            if (ind < ret.length)
                ret[ind] *= -1;
         }        
         return ret;
    }
    
    /**
     * TODO: same as encodeDate(long, String)'s
     * 
     * @param millis: number of millis second from UTC in the sepcified timezone
     * @param tz
     * @return the (MySQL) encoded TIME value
     */
    public static int encodeTime(long millis, String tz)
    {
        DateTime dt = new DateTime(millis, DateTimeZone.forID(tz));

        return (int)(dt.getHourOfDay() * DATETIME_HOUR_SCALE  
                        + dt.getMinuteOfHour() * DATETIME_MIN_SCALE
                        + dt.getSecondOfMinute());
    }
    
    public static int encodeTime(long val[])
    {
        int n = HOUR_INDEX;
        int sign = 1;
        
        while (n < val.length && val[n] >= 0)
            ++n;
        
        if (n < val.length)
            val[n] = val[n] * (sign = -1);

        
        return (int)(val[HOUR_INDEX] * DATETIME_HOUR_SCALE
                    + val[MIN_INDEX] * DATETIME_MIN_SCALE
                    + val[SEC_INDEX]) * sign;
    }
    
    public static int encodeTime(long hr, long min, long sec, TExecutionContext context)
    {
        if (min < 0 || sec < 0)
            throw new InvalidParameterValueException("Invalid time value");
      
        int mul;
        
        if (hr < 0)
            hr *= mul = -1;
        else if (min < 0)
            min *= mul = -1;
        else if (sec < 0)
            sec *= mul = -1;
        else
            mul = 1;
        
        long ret = mul * (hr * DATETIME_HOUR_SCALE + min * DATETIME_MIN_SCALE + sec);
        return (int)CastUtils.getInRange(TIME_MAX, TIME_MIN, ret, context);
    }

    public static int parseTimestamp (String ts, String tz, TExecutionContext context)
    {
        Matcher m = DATE_PATTERN.matcher(ts.trim());

            if (!m.matches() || m.group(DATE_GROUP) == null) 
            throw new InvalidDateFormatException("datetime", ts);

        String year = m.group(DATE_YEAR_GROUP);
        String month = m.group(DATE_MONTH_GROUP);
        String day = m.group(DATE_DAY_GROUP);
        String hour = "0";
        String minute = "0";
        String seconds = "0";

        if (m.group(TIME_GROUP) != null)
        {
            hour = m.group(TIME_HOUR_GROUP);
            minute = m.group(TIME_MINUTE_GROUP);
            seconds = m.group(TIME_SECOND_GROUP);
        }

        try
        {
            long millis = new DateTime(Integer.parseInt(year),
                                       Integer.parseInt(month),
                                       Integer.parseInt(day),
                                       Integer.parseInt(hour),
                                       Integer.parseInt(minute),
                                       Integer.parseInt(seconds),
                                       0,
                                       DateTimeZone.forID(tz)
                                      ).getMillis();
            return (int)CastUtils.getInRange(TIMESTAMP_MAX, TIMESTAMP_MIN, millis / 1000L, TS_ERROR_VALUE, context);
        }
        catch (IllegalFieldValueException | NumberFormatException e)
        {
            context.warnClient(new InvalidDateFormatException("timestamp", ts));
            return 0; // e.g. SELECT UNIX_TIMESTAMP('1920-21-01 00:00:00') -> 0
        }
    }

    public static long[] decodeTimestamp(long ts, String tz) 
    {
        DateTime dt = new DateTime(ts * 1000L, DateTimeZone.forID(tz));
        
        return new long[]
        {
            dt.getYear(),
            dt.getMonthOfYear(),
            dt.getDayOfMonth(),
            dt.getHourOfDay(),
            dt.getMinuteOfHour(),
            dt.getSecondOfMinute()
        }; // TODO: fractional seconds
    }

    public static int encodeTimestamp(long val[], String tz, TExecutionContext context)
    {
        DateTime dt = new DateTime((int)val[YEAR_INDEX], (int)val[MONTH_INDEX], (int)val[DAY_INDEX],
                                   (int)val[HOUR_INDEX], (int)val[MIN_INDEX], (int)val[SEC_INDEX], 0,
                                   DateTimeZone.forID(tz));
        
        return (int)CastUtils.getInRange(TIMESTAMP_MAX, TIMESTAMP_MIN, dt.getMillis() / 1000L, TS_ERROR_VALUE, context);
    }

    public static long encodeTimetamp(long millis, TExecutionContext context)
    {
        return CastUtils.getInRange(TIMESTAMP_MAX, TIMESTAMP_MIN, millis / 1000L, TS_ERROR_VALUE, context);
    }

    /**
     * @param val array encoding year, month, day, hour, min, sec
     * @param tz
     * @return a unix timestamp (w/o range-checking)
     */
    public static int getTimestamp(long val[], String tz)
    {
        return (int)(new DateTime((int)val[YEAR_INDEX], (int)val[MONTH_INDEX], (int)val[DAY_INDEX],
                            (int)val[HOUR_INDEX], (int)val[MIN_INDEX], (int)val[SEC_INDEX], 0,
                            DateTimeZone.forID(tz)).getMillis() / 1000L);
    }

    public static String timestampToString(long ts, String tz)
    {
        long ymd[] = decodeTimestamp(ts, tz);
        
        return String.format("%04d-%02d-%02d %02d:%02d:%02d", 
                            ymd[YEAR_INDEX], ymd[MONTH_INDEX], ymd[DAY_INDEX],
                            ymd[HOUR_INDEX], ymd[MIN_INDEX], ymd[SEC_INDEX]);
    }

    public static boolean isValidDatetime (long ymdhms[])
    {
        return ymdhms != null && isValidDayMonth(ymdhms) && isValidHrMinSec(ymdhms, true);
    }
    
    public static boolean isValidHrMinSec (long hms[], boolean shortTime)
    {
        // if time is from a DATETIME
        if (shortTime)
        {
            return hms[HOUR_INDEX] >= 0 && hms[HOUR_INDEX] < 24
                    && hms[MIN_INDEX] >= 0 && hms[MIN_INDEX] < 60 
                    && hms[SEC_INDEX] >= 0 && hms[SEC_INDEX] < 60;
            
        }
        else // if TIME is NOT from a DATETIME
        {
            // hh:mm:ss
            // One (and only one) of these three parts can be negative
            // and that would be the first part that is non-zero
            //
            // For eg., -12:13:12, or 00:-13:12 or 00:00:-12
            //
            // This is enforced by the decodeTime method, but just check to be sure!
            assert hms[HOUR_INDEX] >= 0 && hms[MIN_INDEX] >= 0 && hms[SEC_INDEX] >= 0
                   || ((hms[HOUR_INDEX] < 0) ^ 
                       (hms[MIN_INDEX] < 0) ^ 
                       (hms[SEC_INDEX] < 0))
                    : "TIME value probably decoded incorrectly!";
            
            return hms[MIN_INDEX] < 60 && hms[SEC_INDEX] < 60;
        }
    }
 
    public static boolean isValidHrMinSec(int hr, int min, int sec, boolean shortTime)
    {
        return hr >= 0 
                && (shortTime ? hr < 24 : true) // if time portion is from a DATETIME, hour should be less than 24
                && min >= 0 && min < 60 
                && sec >= 0 && sec < 60;
    }
    
    public static boolean isZeroDayMonth(long ymd[])
    {
        return ymd[DAY_INDEX] == 0 || ymd[MONTH_INDEX] == 0;
    }

    public static boolean isValidDayMonth(int year, int month, int day)
    {
        long last = getLastDay(year, month);
        return last > 0 && day <= last;
    }

    public static boolean isValidDayMonth(long ymd[])
    {
        long last = getLastDay(ymd);
        return last > 0 && ymd[DAY_INDEX] <= last;
    }
        
    public static long getLastDay(int year, int month)
    {
        switch(month)
        {
            case 2:
                return year % 400 == 0 || year % 4 == 0 && year % 100 != 0 ? 29L : 28L;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30L;
            case 3:
            case 1:
            case 5:
            case 7:
            case 8:
            case 10:
            case 0:
            case 12:
                return 31L;
            default:
                return -1;
        }
    }

    public static long getLastDay(long ymd[])
    {
        switch ((int) ymd[1])
        {
            case 2:
                return ymd[0] % 400 == 0 || ymd[0] % 4 == 0 && ymd[0] % 100 != 0 ? 29L : 28L;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30L;
            case 3:
            case 1:
            case 5:
            case 7:
            case 8:
            case 10:
            case 0:
            case 12:
                return 31L;
            default:
                return -1;
        }
    }

    public static final int YEAR_INDEX = 0;
    public static final int MONTH_INDEX = 1;
    public static final int DAY_INDEX = 2;
    public static final int HOUR_INDEX = 3;
    public static final int MIN_INDEX = 4;
    public static final int SEC_INDEX = 5;
    
    private static final int DATE_YEAR = 10000;
    private static final int DATE_MONTH = 100;

    private static final long DATETIME_DATE_SCALE = 1000000L;
    private static final long DATETIME_YEAR_SCALE = 10000L * DATETIME_DATE_SCALE;
    private static final long DATETIME_MONTH_SCALE = 100L * DATETIME_DATE_SCALE;
    private static final long DATETIME_DAY_SCALE = 1L * DATETIME_DATE_SCALE;
    private static final long DATETIME_HOUR_SCALE = 10000L;
    private static final long DATETIME_MIN_SCALE = 100L;
    
    private static final int TIME_HOURS_SCALE = 10000;
    private static final int TIME_MINUTES_SCALE = 100;
    
    private static final int DATE_GROUP = 1;
    private static final int DATE_YEAR_GROUP = 2;
    private static final int DATE_MONTH_GROUP = 3;
    private static final int DATE_DAY_GROUP = 4;
    private static final int TIME_GROUP = 5;
    private static final int TIME_HOUR_GROUP = 7;
    private static final int TIME_MINUTE_GROUP = 8;
    private static final int TIME_SECOND_GROUP = 9;
    private static final int TIME_FRAC_GROUP = 10;
    private static final int TIME_TIMEZONE_GROUP = 11;
    private static final Pattern DATE_PATTERN 
            = Pattern.compile("^((\\d+)-(\\d+)-(\\d+))(([T]{1}|\\s+)(\\d+):(\\d+):(\\d+)(\\.\\d+)?)?[Z]?(\\s*[+-]\\d+:\\d+(:\\d+)?)?$");
    
    private static final int TIME_WITH_DAY_DAY_GROUP = 2;
    private static final int TIME_WITH_DAY_HOUR_GROUP = 3;
    private static final int TIME_WITH_DAY_MIN_GROUP = 4;
    private static final int TIME_WITH_DAY_SEC_GROUP = 5;
    private static final Pattern TIME_WITH_DAY_PATTERN
            = Pattern.compile("^(([-+]?\\d+)\\s+(\\d+):(\\d+):(\\d+)(\\.\\d+)?[Z]?(\\s*[+-]\\d+:\\d+(:\\d+)?)?)?$");

    private static final int TIME_WITHOUT_DAY_HOUR_GROUP = 2;
    private static final int TIME_WITHOUT_DAY_MIN_GROUP = 3;
    private static final int TIME_WITHOUT_DAY_SEC_GROUP = 4;
    private static final Pattern TIME_WITHOUT_DAY_PATTERN
            = Pattern.compile("^(([-+]?\\d+):(\\d+):(\\d+)(\\.\\d+)?[Z]?(\\s*[+-]\\d+:\\d+(:\\d+)?)?)?$");

    // delimiter for a date/time/datetime string. MySQL allows almost anything to be the delimiter
    private static final String DELIM = "\\W";
    
    // upper and lower limit of TIMESTAMP value
    // as per http://dev.mysql.com/doc/refman/5.5/en/datetime.html
    public static final long TIMESTAMP_MIN = DateTime.parse("1970-01-01T00:00:01Z").getMillis();
    public static final long TIMESTAMP_MAX = DateTime.parse("2038-01-19T03:14:07Z").getMillis();
    public static final long TS_ERROR_VALUE = 0L;
    
    // upper and lower limti of TIME value
    // as per http://dev.mysql.com/doc/refman/5.5/en/time.html
    public static final int TIME_MAX = 8385959;
    public static final int TIME_MIN = -8385959;
    
    private static final EnumSet<StringType> validTypes = EnumSet.of(StringType.DATETIME_ST,
                                                                     StringType.DATE_ST,
                                                                     StringType.TIME_ST);
    public static boolean isValidType(StringType stType)
    {
        return validTypes.contains(stType);
    }
    
    public static enum StringType
    {
        DATE_ST, DATETIME_ST, TIME_ST,
        INVALID_DATE_ST, INVALID_DATETIME_ST, INVALID_TIME_ST,
        UNPARSABLE
    }
}

