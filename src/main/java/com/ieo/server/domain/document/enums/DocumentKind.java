package com.ieo.server.domain.document.enums;

import java.util.List;

/**
 * 서류 종류 enum이 함께 갖는 것. 차주는 DocumentType, 주선사는 BrokerDocumentType이고
 * 응답(DocumentTypeResponse)과 심사 계산(DocumentReview)은 둘을 같은 방식으로 다룬다.
 */
public interface DocumentKind {

	/** enum 이름. 응답의 type 값이다 */
	String name();

	String getLabel();

	/** 필수 서류인지. 선택·조건부 서류는 인증 여부에 들어가지 않는다 */
	boolean isRequired();

	/** 운영자가 사진을 보고 확인할 항목 */
	List<String> getChecks();
}
