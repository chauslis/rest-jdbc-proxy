CREATE TABLE CUSTOMER
(	ID NUMBER NOT NULL ENABLE,
     FIRST_NAME VARCHAR2(100) NOT NULL ENABLE,
     LAST_NAME VARCHAR2(100) NOT NULL ENABLE
);

CREATE OR REPLACE PACKAGE test_pkh
AS

    PROCEDURE tst_proc;
    FUNCTION tst_function(aN VARCHAR2 )
        return VARCHAR2;
    PROCEDURE proc_with_Param(Id NUMBER);
    FUNCTION tst_functionInt(aN NUMBER )
        return VARCHAR2;
END;
/
CREATE OR REPLACE PACKAGE BODY test_pkh
IS

    PROCEDURE tst_proc
        is
    BEGIN
        NULL;
    END;
    FUNCTION tst_function(aN VARCHAR2  ) return VARCHAR2
        is
    BEGIN
        RETURN 1;
    END;
    PROCEDURE proc_with_Param(Id NUMBER)
        IS
    BEGIN
        NULL;
    END;
    FUNCTION tst_functionInt(aN Number )
        return VARCHAR2
        is
    BEGIN
        RETURN 1;
    END;
END;
/