CREATE TABLE CUSTOMER
(	ID NUMBER NOT NULL ENABLE,
     FIRST_NAME VARCHAR2(100) NOT NULL ENABLE,
     LAST_NAME VARCHAR2(100) NOT NULL ENABLE
)
/
INSERT INTO CUSTOMER (ID, FIRST_NAME, LAST_NAME) VALUES(2, 'Chloe', 'O''Brian')
/
INSERT INTO CUSTOMER (ID, FIRST_NAME, LAST_NAME) VALUES(3, 'Kim', 'Bauer')
/
INSERT INTO CUSTOMER (ID, FIRST_NAME, LAST_NAME) VALUES(4, 'David', 'Palmer')
/
INSERT INTO CUSTOMER (ID, FIRST_NAME, LAST_NAME) VALUES(5, 'Michelle', 'Dessler')
/
INSERT INTO CUSTOMER (ID, FIRST_NAME, LAST_NAME) VALUES(6, 'Jack', 'Bauer')
/
INSERT INTO CUSTOMER (ID, FIRST_NAME, LAST_NAME) VALUES(7, 'Chloe', 'O''Brian')
/
INSERT INTO CUSTOMER (ID, FIRST_NAME, LAST_NAME) VALUES(8, 'Kim', 'Bauer')
/
INSERT INTO CUSTOMER (ID, FIRST_NAME, LAST_NAME) VALUES(9, 'David', 'Palmer')
/
INSERT INTO CUSTOMER (ID, FIRST_NAME, LAST_NAME) VALUES(10, 'Michelle', 'Dessler')
/
INSERT INTO CUSTOMER (ID, FIRST_NAME, LAST_NAME) VALUES(11, 'Jack', 'Bauer')
/

ALTER SESSION SET CURRENT_SCHEMA=GT;
CREATE OR REPLACE PACKAGE GT.test_pkh
AS

    PROCEDURE tst_proc;
    FUNCTION tst_function(aN VARCHAR2 )
        return VARCHAR2;
    PROCEDURE proc_with_Param(Id NUMBER);
    FUNCTION tst_functionInt(aN NUMBER )
        return VARCHAR2;
    PROCEDURE proc_with_OutParam(Id NUMBER, Name VARCHAR2, out1 OUT VARCHAR2, OUT2 OUT VARCHAR2, OUT3 OUT VARCHAR2, p VARCHAR2);
    FUNCTION rc_function(aN VARCHAR2  ) return SYS_REFCURSOR ;
END;
/
CREATE OR REPLACE PACKAGE BODY GT.test_pkh
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

    PROCEDURE proc_with_OutParam(Id NUMBER, Name VARCHAR2, out1 OUT VARCHAR2, OUT2 OUT VARCHAR2, OUT3 OUT VARCHAR2, p VARCHAR2)
        IS
    BEGIN
        out1 := 'out1';
        out2 := Name;
        out3 := p;
    END;

    FUNCTION rc_function(aN VARCHAR2  ) return SYS_REFCURSOR
        IS
        vResult SYS_REFCURSOR;
    BEGIN
        OPEN vResult FOR
            SELECT 1 N, SYSDATE d, 'Line !' S FROM DUAL
            UNION
            SELECT 2 N, SYSDATE d, 'Line 2' S FROM DUAL
            UNION
            SELECT 3 N, SYSDATE d, 'Line 3' S FROM DUAL;

        RETURN vResult;
    END;


END;
/